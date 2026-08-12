// src/main/java/com/sc2006group5/car_one_stop/controller/auth/AuthController.java
package com.sc2006group5.car_one_stop.controller.auth;

import com.sc2006group5.car_one_stop.dto.auth.*;
import com.sc2006group5.car_one_stop.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req)); //done
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req)); //done
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Void> verifyResetOtp(@Valid @RequestBody VerifyResetOtpRequest req) {
        authService.verifyResetOtp(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-reset-otp")
    public ResponseEntity<Void> resendResetOtp(@Valid @RequestBody ResendResetOtpRequest req) {
        authService.resendResetOtp(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@AuthenticationPrincipal Long userId, @Valid @RequestBody VerifyRequest req){
        return ResponseEntity.ok(authService.verify(userId, req)); //done
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<Void> resendOtp(@AuthenticationPrincipal Long userId) {
        authService.resendOtp(userId);
        return ResponseEntity.ok().build();
    }

}
