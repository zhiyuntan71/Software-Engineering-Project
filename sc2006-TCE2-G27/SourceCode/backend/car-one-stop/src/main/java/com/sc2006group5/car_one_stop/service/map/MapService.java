package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.common.external.lta.LtaClient;
import com.sc2006group5.car_one_stop.common.external.hdb.HdbClient;
import com.sc2006group5.car_one_stop.common.external.ocm.OcmClient;
import com.sc2006group5.car_one_stop.dto.map.*;
import com.sc2006group5.car_one_stop.repository.booking.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapService {

    private final LtaClient ltaClient;
    private final HdbClient hdbClient;
    private final OcmClient ocmClient;

    private final BookingRepository bookingRepository;

    /** Stations within this distance (metres) of an LTA station are considered duplicates. */
    private static final double DEDUP_RADIUS_METERS = 50.0;

    public List<EVChargerDto> nearbyEVChargers(double lat, double lng, double radiusKm) {
        double radiusMeters = radiusKm * 1000;

        // ── LTA (live status) ────────────────────────────────────────────────
        List<EVChargerDto> ltaAll = ltaClient.fetchAllEVChargers();
        Set<String> occupiedIds = new HashSet<>(bookingRepository.findOccupiedSlotIds());

        List<EVChargerDto> ltaNearby = ltaAll.stream()
                .filter(ev -> ev.getLatitude() != 0 && ev.getLongitude() != 0)
                .filter(ev -> haversineMeters(lat, lng, ev.getLatitude(), ev.getLongitude()) <= radiusMeters)
                .toList();

        // Mark occupied slots as unavailable
        for (EVChargerDto ev : ltaNearby) {
            if (ev.getChargingPoints() == null) continue;
            for (Object cpObj : ev.getChargingPoints()) {
                if (!(cpObj instanceof Map<?, ?> cp)) continue;
                Object plugTypesObj = cp.get("plugTypes");
                if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
                for (Object ptObj : plugTypes) {
                    if (!(ptObj instanceof Map<?, ?> pt)) continue;
                    Object evIdsObj = pt.get("evIds");
                    if (!(evIdsObj instanceof List<?> evIds)) continue;
                    for (Object evIdObj : evIds) {
                        if (!(evIdObj instanceof Map<?, ?> evId)) continue;
                        String evCpId = String.valueOf(evId.get("evCpId"));
                        if (occupiedIds.contains(evCpId)) {
                            ((Map<String, Object>) cp).put("status", "0");
                        }
                    }
                }
            }
        }

        // ── OCM (no live status) — deduplicate against LTA ──────────────────
        List<EVChargerDto> ocmAll = ocmClient.fetchAllEVChargers();
        List<EVChargerDto> ocmNearby = ocmAll.stream()
                .filter(ev -> haversineMeters(lat, lng, ev.getLatitude(), ev.getLongitude()) <= radiusMeters)
                .filter(ev -> !isDuplicate(ev, ltaNearby))
                .toList();

        log.info("EV nearby: {} LTA + {} OCM-only stations within {}km",
                ltaNearby.size(), ocmNearby.size(), radiusKm);

        List<EVChargerDto> merged = new ArrayList<>(ltaNearby.size() + ocmNearby.size());
        merged.addAll(ltaNearby);
        merged.addAll(ocmNearby);
        return merged;
    }

    /** Returns true if the OCM station is within DEDUP_RADIUS_METERS of any LTA station. */
    private boolean isDuplicate(EVChargerDto ocmStation, List<EVChargerDto> ltaStations) {
        for (EVChargerDto lta : ltaStations) {
            if (haversineMeters(ocmStation.getLatitude(), ocmStation.getLongitude(),
                    lta.getLatitude(), lta.getLongitude()) <= DEDUP_RADIUS_METERS) {
                return true;
            }
        }
        return false;
    }

    public List<CarparkDto> nearbyCarparks(double lat, double lng, double radiusKm) {
        List<CarparkDto> coords = hdbClient.fetchAllCarparkCoords();
        List<CarparkAvailabilityDto> availability = hdbClient.fetchAvailability();

        Map<String, Integer> availMap = new HashMap<>();
        availability.forEach(a -> availMap.put(a.getCarparkNumber(), a.getLotsAvailable()));

        return coords.stream()
                .filter(cp -> haversineMeters(lat, lng, cp.getLatitude(), cp.getLongitude()) <= radiusKm * 1000)
                .map(cp -> CarparkDto.builder()
                        .carParkID(cp.getCarParkID())
                        .development(cp.getDevelopment())
                        .latitude(cp.getLatitude())
                        .longitude(cp.getLongitude())
                        .availableLots(availMap.getOrDefault(cp.getCarParkID(), -1))
                        .agency("HDB")
                        .build())
                .toList();
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}