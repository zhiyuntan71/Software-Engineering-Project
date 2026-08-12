// src/main/java/com/sc2006group5/car_one_stop/dto/auth/LoginRequest.java
package com.sc2006group5.car_one_stop.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String Email,
        @NotBlank String password
) {}