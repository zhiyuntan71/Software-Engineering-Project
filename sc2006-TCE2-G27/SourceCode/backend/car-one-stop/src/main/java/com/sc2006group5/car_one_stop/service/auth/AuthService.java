package com.sc2006group5.car_one_stop.service.auth;

import com.sc2006group5.car_one_stop.config.auth.JwtUtil;
import com.sc2006group5.car_one_stop.dto.auth.*;
import com.sc2006group5.car_one_stop.entity.auth.OTPToken;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.enums.auth.UserRole;
import com.sc2006group5.car_one_stop.exceptions.InvalidOtpException;
import com.sc2006group5.car_one_stop.mapper.auth.UserMapper;
import com.sc2006group5.car_one_stop.repository.auth.OTPTokenRepository;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Random;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final OTPTokenRepository otpTokenRepository;

    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public RegisterResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            User existing = userRepository.findByEmail(req.email()).get();
            if (existing.getRole().equals(UserRole.USER_UNVERIFIED)) {
                OTPToken otp = otpTokenRepository.findByUser(existing).orElse(null);

                if (otp == null || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
                    // expired — delete old user, allow re-register
                    otpTokenRepository.deleteAllByUser(existing);
                    userRepository.delete(existing);
                    throw new ResponseStatusException(GONE, "Verification expired, please register again");
                } else {
                    // not expired — tell them to verify
                    throw new ResponseStatusException(FORBIDDEN, "Account exists, please verify your email");  // 403
                }
            } else {
                // fully verified — conflict
                throw new ResponseStatusException(CONFLICT, "Email already in use");  // 409
            }
        }


        User user = User.builder()
                .email(req.email().trim().toLowerCase())
                .username(req.username().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .verified(false)
                .role(UserRole.USER_UNVERIFIED)
                .carType(req.carType())
                .carModel(req.carModel())
                .chargingType(req.chargingType())
                .build();

        String otp = String.format("%06d", new Random().nextInt(999999));
        OTPToken otpToken = OTPToken.builder()
                .otpToken(otp)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .user(user)
                .build();

        User saved = userRepository.save(user);
        otpTokenRepository.save(otpToken);

        try {
            emailService.sendOtpEmail(saved.getEmail(), otp);
        } catch (RuntimeException ex) {
            // Keep registration successful even if SMTP is unavailable in local/dev.
            log.warn("OTP email failed for {}: {}", saved.getEmail(), ex.getMessage());
        }

        String token = jwtUtil.generateToken(saved.getId(), UserRole.USER_UNVERIFIED);

        RegisterResponse resp = userMapper.toRegisterResponse(saved);
        return new RegisterResponse(
                resp.userId(),
                resp.username(),
                resp.email(),
                token,
                resp.carType(),
                resp.carModel(),
                resp.chargingType());
    }

    public LoginResponse login(LoginRequest req) {
        String key = req.Email().trim();
        User user = userRepository.findByEmail(key.toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "User Cant be found "));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(BAD_REQUEST, "Wrong Credentials");                    // 400
        }
        if (user.isBanned()) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Your account has been banned");   // 423
        }
        if(user.getRole().equals(UserRole.USER_UNVERIFIED)){
            OTPToken otp = otpTokenRepository.findByUser(user).orElse(null);

            if (otp == null || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
                // expired — delete user, force re-register
                otpTokenRepository.deleteAllByUser(user);
                userRepository.delete(user);
                throw new ResponseStatusException(GONE, "Verification expired, please register again");  // 410
            } else {
                // not expired — redirect to verify
                throw new ResponseStatusException(FORBIDDEN, "Please verify your email first");  // 403
            }
        }
        String JWTtoken = jwtUtil.generateToken(user.getId(), user.getRole());
        LoginResponse resp = userMapper.toLoginResponse(user);
        return new LoginResponse(resp.userId(), resp.username(), resp.email(), JWTtoken, resp.carType(),resp.carModel(),resp.chargingType());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepository.findByEmail(req.email().trim().toLowerCase())
                .ifPresent(user -> {
                    otpTokenRepository.deleteAllByUser(user);

                    String otp = String.format("%06d", new Random().nextInt(999999));
                    OTPToken otpToken = OTPToken.builder()
                            .otpToken(otp)
                            .expiresAt(LocalDateTime.now().plusMinutes(15))
                            .user(user)
                            .build();

                    otpTokenRepository.save(otpToken);
                    try {
                        emailService.sendResetOtpEmail(req.email(), otp);
                    } catch (RuntimeException ex) {
                        // Keep registration successful even if SMTP is unavailable in local/dev.
                        log.warn("Password Reset OTP email failed for {}: {}", req.email(), ex.getMessage());
                    }

                    System.out.println("[DEV] Reset OTPtoken for " + user.getEmail() + ": " + otp);
                });
    }

    public void resetPassword(ResetPasswordRequest req) {
        if (req.newPassword() == null || req.newPassword().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Password is required");
        }

        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        String otpInput = req.OTPtoken() == null ? "" : req.OTPtoken().trim();
        String legacyToken = req.token() == null ? "" : req.token().trim();

        // Preferred flow: email + OTPtoken
        if (!email.isBlank() && !otpInput.isBlank()) {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken"));

            OTPToken otpToken = otpTokenRepository.findByUser(user)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken"));

            if (otpToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                otpTokenRepository.delete(otpToken);
                throw new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken");
            }

            if (!otpToken.getOtpToken().equals(otpInput)) {
                throw new InvalidOtpException();
            }

            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            userRepository.save(user);
            otpTokenRepository.delete(otpToken);
            return;
        }

        // Backward-compatible flow: token + newPassword
        if (!legacyToken.isBlank()) {
            OTPToken otpToken = otpTokenRepository.findFirstByOtpTokenOrderByExpiresAtDesc(legacyToken)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken"));

            if (otpToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                otpTokenRepository.delete(otpToken);
                throw new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken");
            }

            User user = otpToken.getUser();
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            userRepository.save(user);
            otpTokenRepository.delete(otpToken);
            return;
        }

        throw new ResponseStatusException(BAD_REQUEST, "Invalid reset payload");
    }

    public void verifyResetOtp(VerifyResetOtpRequest req) {
        User user = userRepository.findByEmail(req.email().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken"));

        OTPToken otpToken = otpTokenRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken"));

        if (otpToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpTokenRepository.delete(otpToken);
            throw new ResponseStatusException(BAD_REQUEST, "Invalid or expired OTPtoken");
        }

        if (!otpToken.getOtpToken().equals(req.OTPtoken().trim())) {
            throw new InvalidOtpException();
        }
    }

    @Transactional
    public void resendResetOtp(ResendResetOtpRequest req) {
        userRepository.findByEmail(req.email().trim().toLowerCase())
                .ifPresent(user -> {
                    otpTokenRepository.deleteAllByUser(user);

                    String otp = String.format("%06d", new Random().nextInt(999999));
                    OTPToken otpToken = OTPToken.builder()
                            .otpToken(otp)
                            .expiresAt(LocalDateTime.now().plusMinutes(15))
                            .user(user)
                            .build();

                    otpTokenRepository.save(otpToken);

                    try {
                        emailService.sendResetOtpEmail(user.getEmail(), otp);
                    } catch (RuntimeException ex) {
                        log.warn("Resend reset OTP email failed for {}: {}", user.getEmail(), ex.getMessage());
                    }
                });
    }

    @Transactional
    public AuthResponse verify(Long userId, VerifyRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User does not exist"));;
        OTPToken storedOTPToken = otpTokenRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("User Did Not Register"));
        if (storedOTPToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            otpTokenRepository.deleteAllByUser(user);
            userRepository.delete(user);
            throw new ResponseStatusException(HttpStatus.GONE, "OTP expired, please register again");
        }
        if(!storedOTPToken.getOtpToken().equals(req.OTPtoken())){
            throw new InvalidOtpException();
        }
        user.setVerified(true);
        user.setRole(UserRole.USER);
        String token = jwtUtil.generateToken(user.getId(), UserRole.USER);
        userRepository.save(user);

        otpTokenRepository.delete(storedOTPToken);

        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), token);
    }

    @Transactional
    public void resendOtp(Long userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User does not exist"));

        if (!UserRole.USER_UNVERIFIED.equals(user.getRole())) {
            throw new ResponseStatusException(FORBIDDEN, "Only unverified users can request OTP");
        }

        String otp = String.format("%06d", new Random().nextInt(999999));
        OTPToken otpToken = otpTokenRepository.findByUser(user)
                .map(existing -> {
                    existing.setOtpToken(otp);
                    existing.setExpiresAt(LocalDateTime.now().plusMinutes(15));
                    return existing;
                })
                .orElseGet(() -> OTPToken.builder()
                        .otpToken(otp)
                        .expiresAt(LocalDateTime.now().plusMinutes(15))
                        .user(user)
                        .build());

        otpTokenRepository.save(otpToken);

        try {
            emailService.sendOtpEmail(user.getEmail(), otp);
        } catch (RuntimeException ex) {
            log.warn("OTP email failed for {}: {}", user.getEmail(), ex.getMessage());
        }
    }
}
