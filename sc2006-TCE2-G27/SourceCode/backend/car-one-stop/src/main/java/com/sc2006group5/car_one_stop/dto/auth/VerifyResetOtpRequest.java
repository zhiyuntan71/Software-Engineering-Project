package com.sc2006group5.car_one_stop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyResetOtpRequest(
        @Email @NotBlank String email,
        @NotBlank String OTPtoken
) {}
