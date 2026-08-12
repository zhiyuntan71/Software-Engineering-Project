// src/main/java/com/sc2006group5/car_one_stop/dto/booking/BookingResponse.java
package com.sc2006group5.car_one_stop.dto.booking;

import com.sc2006group5.car_one_stop.enums.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        String bookingId,
        String evChargingPointId,
        String locationName,
        String lot,
        String plugType,
        String priceDisplay,
        String priceType,
        String powerRating,
        String currentType,
        String voltage,
        BookingStatus status,
        Instant createdAt,
        Instant reservedTime,
        Instant checkInTime,
        int gracePeriodMinutes,
        Instant startTime,
        Instant endTime,
        BigDecimal bookingFee
) {}
