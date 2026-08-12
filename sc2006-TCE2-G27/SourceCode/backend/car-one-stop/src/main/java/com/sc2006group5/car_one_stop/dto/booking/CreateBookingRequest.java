// src/main/java/com/sc2006group5/car_one_stop/dto/booking/CreateBookingRequest.java
package com.sc2006group5.car_one_stop.dto.booking;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingRequest(
        @NotBlank String evChargingPointId
) {}