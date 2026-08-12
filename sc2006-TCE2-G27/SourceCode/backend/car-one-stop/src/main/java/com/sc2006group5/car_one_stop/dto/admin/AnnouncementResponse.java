package com.sc2006group5.car_one_stop.dto.admin;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        String title,
        String message,
        LocalDateTime createdAt) {
}
