package com.sc2006group5.car_one_stop.dto.admin;

public record UserAdminResponse(
        Long id,
        String username,
        String email,
        String role,
        Boolean banned) {
}
