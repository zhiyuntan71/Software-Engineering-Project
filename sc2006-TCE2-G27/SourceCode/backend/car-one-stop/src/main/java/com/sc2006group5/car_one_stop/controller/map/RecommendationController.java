package com.sc2006group5.car_one_stop.controller.map;

import com.sc2006group5.car_one_stop.dto.map.CarParkRecommendationResult;
import com.sc2006group5.car_one_stop.dto.map.EVRecommendationItemDto;
import com.sc2006group5.car_one_stop.dto.map.PreferenceUpsertRequest;
import com.sc2006group5.car_one_stop.dto.map.RecommendationItemDto;
import com.sc2006group5.car_one_stop.dto.map.UserPreferenceDto;
import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import com.sc2006group5.car_one_stop.enums.map.RecommendationPreference;
import com.sc2006group5.car_one_stop.service.map.EVRecommendationService;
import com.sc2006group5.car_one_stop.service.map.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final EVRecommendationService evRecommendationService;

    @GetMapping
    public ResponseEntity<List<RecommendationItemDto>> recommend(
            @RequestParam FacilityType type,
            @RequestParam RecommendationPreference preference,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") int radius,
            @RequestParam(defaultValue = "3") int limit
    ) {
        return ResponseEntity.ok(recommendationService.recommend(type, preference, lat, lng, radius, limit));
    }

    /** Returns all hard-filtered candidates (blue pins) + top recommendation (yellow pin). */
    @GetMapping("/carparks")
    public ResponseEntity<CarParkRecommendationResult> recommendCarparks(
            @RequestParam RecommendationPreference preference,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") int radius
    ) {
        return ResponseEntity.ok(recommendationService.recommendCarparks(preference, lat, lng, radius));
    }

    @GetMapping("/ev-chargers")
    public ResponseEntity<List<EVRecommendationItemDto>> recommendEVChargers(
            @RequestParam RecommendationPreference preference,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") int radius
    ) {
        return ResponseEntity.ok(evRecommendationService.recommendEVChargers(preference, lat, lng, radius));
    }

    @GetMapping("/preferences")
    public ResponseEntity<UserPreferenceDto> getPreferences() {
        return ResponseEntity.ok(UserPreferenceDto.from(recommendationService.getPreferences()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserPreferenceDto> upsertPreferences(@RequestBody PreferenceUpsertRequest req) {
        return ResponseEntity.ok(UserPreferenceDto.from(recommendationService.upsertPreferences(req)));
    }
}
