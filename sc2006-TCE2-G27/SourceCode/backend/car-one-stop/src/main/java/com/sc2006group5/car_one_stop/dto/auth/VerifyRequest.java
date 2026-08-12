package com.sc2006group5.car_one_stop.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
        @NotBlank String OTPtoken
) {
}
