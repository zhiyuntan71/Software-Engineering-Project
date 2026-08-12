// src/main/java/com/sc2006group5/car_one_stop/dto/auth/ForgotPasswordRequest.java
package com.sc2006group5.car_one_stop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Email @NotBlank String email
) {}