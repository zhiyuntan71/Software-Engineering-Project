package com.sc2006group5.car_one_stop.dto.map;

import java.util.List;

/** Response for GET /api/recommendations/carparks.
 *  candidates     — every carpark that passed hard filtering (eligible pins).
 *  recommendations — top-3 scored carparks in rank order (rank 1 green, 2-3 yellow). */
public record CarParkRecommendationResult(
        List<CandidateItemDto> candidates,
        List<RecommendationItemDto> recommendations
) {}
