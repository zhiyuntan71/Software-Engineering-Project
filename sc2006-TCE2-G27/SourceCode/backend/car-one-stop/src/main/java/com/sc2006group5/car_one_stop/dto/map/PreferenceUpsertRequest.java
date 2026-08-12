package com.sc2006group5.car_one_stop.dto.map;

import java.math.BigDecimal;

public record PreferenceUpsertRequest(
        BigDecimal maxHourlyRate,
        Integer minAvailableLots,
        Boolean requireSheltered,
        Double weightDistance,
        Double weightPrice,
        Double weightAvailability,
        Integer parkingDurationHours,
        Integer evParkingDurationHours
) {}
