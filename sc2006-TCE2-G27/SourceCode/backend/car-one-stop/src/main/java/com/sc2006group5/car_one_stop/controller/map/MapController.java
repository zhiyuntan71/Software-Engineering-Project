package com.sc2006group5.car_one_stop.controller.map;

import com.sc2006group5.car_one_stop.dto.map.*;
import com.sc2006group5.car_one_stop.service.map.MapService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapController {

    private final MapService mapService;

    @Value("${google.maps.api-key}")
    private String googleMapsApiKey;

    @GetMapping("/nearby/carparks")
    public ResponseEntity<List<CarparkDto>> nearbyCarparks(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "2.0") double radiusKm
    ) {
        return ResponseEntity.ok(mapService.nearbyCarparks(lat, lng, radiusKm));
    }

    @GetMapping("/nearby/ev-chargers")
    public ResponseEntity<List<EVChargerDto>> nearbyEVChargers(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "2.0") double radiusKm
    ) {
        return ResponseEntity.ok(mapService.nearbyEVChargers(lat, lng, radiusKm));
    }

    @GetMapping("/config/maps-key")
    public ResponseEntity<Map<String, String>> getMapsApiKey() {
        return ResponseEntity.ok(Map.of("apiKey", googleMapsApiKey));
    }
}