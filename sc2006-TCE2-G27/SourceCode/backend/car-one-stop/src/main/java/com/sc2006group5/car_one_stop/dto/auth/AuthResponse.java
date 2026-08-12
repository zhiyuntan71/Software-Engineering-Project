// src/main/java/com/sc2006group5/car_one_stop/dto/auth/AuthResponse.java
package com.sc2006group5.car_one_stop.dto.auth;

public record AuthResponse(
        Long userId,
        String username,
        String email,
        String token
) {}