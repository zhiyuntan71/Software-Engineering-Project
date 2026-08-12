// src/main/java/com/sc2006group5/car_one_stop/dto/auth/ResetPasswordRequest.java
package com.sc2006group5.car_one_stop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Email String email,
        String OTPtoken,
        String token,
        @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$",
                message = "Password must include uppercase, lowercase, number, and special character"
        )
        String newPassword
) {}
