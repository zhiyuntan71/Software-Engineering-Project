package com.sc2006group5.car_one_stop.dto.map;

import com.sc2006group5.car_one_stop.enums.map.FacilityType;

import java.math.BigDecimal;
import java.time.Instant;

public record RecommendationItemDto(
        Long id,
        FacilityType type,
        String name,
        String address,
        double lat,
        double lng,
        BigDecimal hourlyRate,
        Integer availableLots,
        Boolean sheltered,
        double distanceMeters,
        Double etaMinutes,
        double score
) {}