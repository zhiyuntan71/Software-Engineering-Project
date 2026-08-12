package com.sc2006group5.car_one_stop.dto.map;

import java.math.BigDecimal;

public record EVRecommendationItemDto(
        String stationName,
        String address,
        double lat,
        double lng,
        String postalCode,
        int availablePoints,
        BigDecimal evChargingCost,
        BigDecimal carparkCost,
        BigDecimal totalCost,
        String nearestCarparkName,
        double distanceMeters,
        Double etaMinutes,
        double score
) {}
