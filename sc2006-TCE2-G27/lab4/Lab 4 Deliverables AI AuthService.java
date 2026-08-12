package com.sc2006group5.car_one_stop.service.auth;

import com.sc2006group5.car_one_stop.dto.*;
import com.sc2006group5.car_one_stop.model.*;
import com.sc2006group5.car_one_stop.repository.*;
import com.sc2006group5.car_one_stop.service.EmailService;
import com.sc2006group5.car_one_stop.util.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private ResetLinkRepository resetLinkRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 15;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;

    // =====================================================================
    // 1. REGISTER
    // =====================================================================
    // Test coverage:
    //   REG-EQ-01: New email, valid fields              → 200 + RegisterResponse + temporary JWT
    //   REG-EQ-02: Existing verified email               → 409 Conflict
    //   REG-EQ-03: Existing unverified + unexpired OTP   → 403 Forbidden
    //   REG-EQ-04: Existing unverified + expired/no OTP  → 410 Gone (delete old, ask re-register)
    //   REG-BV-01..08: Username 3-50 chars, Password 8-72 chars (handled by @Valid on controller)
    // =====================================================================
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();

            // Case REG-EQ-02: Already verified — conflict
            if (existingUser.isEmailVerified() || existingUser.getRole() == Role.USER) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An account with this email already exists.");
            }

            // Unverified user exists — check OTP status
            Optional<Otp> existingOtp = otpRepository.findByUserId(existingUser.getUserId());

            if (existingOtp.isPresent() && !existingOtp.get().isExpired()) {
                // Case REG-EQ-03: Unverified with valid OTP still pending
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A verification is already pending. Please check your email for the OTP.");
            }

            // Case REG-EQ-04: Unverified with expired or missing OTP — clean up and allow re-register
            otpRepository.deleteByUserId(existingUser.getUserId());
            userRepository.delete(existingUser);
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Previous registration expired. Please register again.");
        }

        // Case REG-EQ-01: Brand new registration
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setUsername(request.getUsername());
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setCarType(request.getCarType());
        newUser.setCarModel(request.getCarModel());
        newUser.setChargeType(request.getChargeType());
        newUser.setEmailVerified(false);
        newUser.setRole(Role.USER_UNVERIFIED);

        User savedUser = userRepository.save(newUser);

        // Generate 6-digit OTP and save with 15-min expiry
        String otpCode = generateOtpCode();
        Otp otp = new Otp();
        otp.setUserId(savedUser.getUserId());
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepository.save(otp);

        // Send OTP via email
        emailService.sendOtpEmail(savedUser.getEmail(), otpCode);

        // Generate temporary JWT for USER_UNVERIFIED
        String token = jwtUtil.generateTemporaryToken(savedUser);

        return new RegisterResponse(
                savedUser.getUserId(),
                savedUser.getEmail(),
                savedUser.getUsername(),
                savedUser.getRole(),
                token
        );
    }

    // =====================================================================
    // 2. LOGIN
    // =====================================================================
    // Test coverage:
    //   LOG-EQ-01: Verified user + correct password       → 200 + LoginResponse + USER JWT
    //   LOG-EQ-02: Existing user + wrong password         → 401 Unauthorized
    //   LOG-EQ-03: Non-existing email                     → 401 Unauthorized
    //   LOG-EQ-04: Unverified user + unexpired OTP        → 403 Forbidden
    //   LOG-EQ-05: Unverified user + expired/missing OTP  → 410 Gone
    // =====================================================================
    public LoginResponse login(LoginRequest request) {
        // Case LOG-EQ-03: User not found
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password."));

        // Case LOG-EQ-02: Wrong password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }

        // Handle unverified users
        if (user.getRole() == Role.USER_UNVERIFIED) {
            Optional<Otp> otpOpt = otpRepository.findByUserId(user.getUserId());

            if (otpOpt.isPresent() && !otpOpt.get().isExpired()) {
                // Case LOG-EQ-04: Still has a valid OTP — tell them to verify
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Account not verified. Please check your email for the OTP.");
            }

            // Case LOG-EQ-05: OTP expired or missing — force re-registration
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Verification expired. Please register again.");
        }

        // Case LOG-EQ-01: Verified user, correct password — success
        String token = jwtUtil.generateToken(user);

        return new LoginResponse(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                token,
                user.getCarType(),
                user.getCarModel(),
                user.getChargeType()
        );
    }

    // =====================================================================
    // 3. VERIFY
    // =====================================================================
    // Test coverage:
    //   VER-EQ-01: USER_UNVERIFIED token + correct OTP   → 200 + AuthResponse, role → USER
    //   VER-EQ-02: USER_UNVERIFIED token + wrong OTP     → 422 Unprocessable Entity
    //   VER-BV-01: OTP differs by 1 char                 → 422 (exact match required)
    // =====================================================================
    @Transactional
    public AuthResponse verify(Long userId, VerifyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found."));

        if (user.getRole() != Role.USER_UNVERIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User is already verified.");
        }

        Otp otp = otpRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No OTP found. Please request a new one."));

        if (otp.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OTP has expired. Please request a new one.");
        }

        // Case VER-EQ-02 / VER-BV-01: OTP must match exactly
        if (!otp.getOtpCode().equals(request.getOtp())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid OTP.");
        }

        // Case VER-EQ-01: Promote user to USER role
        user.verifyEmail();
        userRepository.save(user);

        // Delete the used OTP record
        otpRepository.deleteByUserId(userId);

        // Generate new full JWT with USER role
        String token = jwtUtil.generateToken(user);

        return new AuthResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole(),
                token
        );
    }

    // =====================================================================
    // 4. RESEND OTP
    // =====================================================================
    // Test coverage:
    //   ROTP-EQ-01: Valid USER_UNVERIFIED token/user → 200, old OTP replaced, new OTP (15 min)
    //   ROTP-EQ-02: USER role token / no auth        → 403/401 (handled by security layer)
    // =====================================================================
    @Transactional
    public void resendOtp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found."));

        if (user.getRole() != Role.USER_UNVERIFIED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only unverified users can request OTP.");
        }

        // Delete any existing OTP for this user
        otpRepository.deleteByUserId(userId);

        // Generate fresh 6-digit OTP with new 15-minute expiry
        String otpCode = generateOtpCode();
        Otp otp = new Otp();
        otp.setUserId(userId);
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpRepository.save(otp);

        // Send via email
        emailService.sendOtpEmail(user.getEmail(), otpCode);
    }

    // =====================================================================
    // 5. FORGOT PASSWORD
    // =====================================================================
    // Test coverage:
    //   FP-EQ-01: Existing email       → 200, reset token generated/sent
    //   FP-EQ-02: Non-existing email   → 200, no user-visible error (prevent enumeration)
    // =====================================================================
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

        // Case FP-EQ-02: Silently do nothing if email doesn't exist (prevent user enumeration)
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();

        // Generate UUID-based reset token with 15-minute TTL
        String resetToken = UUID.randomUUID().toString();

        ResetLink resetLink = new ResetLink();
        resetLink.setUserId(user.getUserId());
        resetLink.setToken(resetToken);
        resetLink.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
        resetLink.setUsed(false);
        resetLinkRepository.save(resetLink);

        // Send reset email
        emailService.sendResetEmail(user.getEmail(), resetToken);
    }

    // =====================================================================
    // 6. RESET PASSWORD
    // =====================================================================
    // Test coverage:
    //   RP-EQ-01: Valid token (unused, unexpired) + valid password → 200, password updated, token marked used
    //   RP-EQ-02: Invalid token                                    → 400 Bad Request
    //   RP-EQ-03: Expired token                                    → 400 Bad Request
    //   RP-EQ-04: Already-used token                               → 400 Bad Request
    //   RP-BV-01..04: newPassword 8-72 chars (handled by @Valid on controller)
    //   RP-BV-05: Reuse same token immediately after reset         → First 200, second 400
    // =====================================================================
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Case RP-EQ-02: Token not found
        ResetLink resetLink = resetLinkRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid reset token."));

        // Case RP-EQ-04 / RP-BV-05: Already used
        if (resetLink.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This reset token has already been used.");
        }

        // Case RP-EQ-03: Expired
        if (resetLink.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This reset token has expired.");
        }

        // Case RP-EQ-01: Valid token — update password and mark token as used
        User user = userRepository.findById(resetLink.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User not found."));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetLink.setUsed(true);
        resetLinkRepository.save(resetLink);
    }

    // =====================================================================
    // HELPER: Generate a secure 6-digit OTP
    // =====================================================================
    private String generateOtpCode() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // ensures 6 digits (100000–999999)
        return String.valueOf(otp);
    }
}
