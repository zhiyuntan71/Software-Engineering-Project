package com.sc2006group5.car_one_stop.dto.map;

/** Minimal carpark data for all facilities that survived hard filtering.
 *  Returned alongside the top recommendation so the frontend can render
 *  blue candidate pins separately from the yellow recommended pin. */
public record CandidateItemDto(
        Long id,
        String name,
        double lat,
        double lng,
        Integer availableLots,
        java.math.BigDecimal hourlyRate
) {}
