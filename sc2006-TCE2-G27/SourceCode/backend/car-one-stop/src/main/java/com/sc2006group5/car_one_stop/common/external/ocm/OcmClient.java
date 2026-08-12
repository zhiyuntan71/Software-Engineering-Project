package com.sc2006group5.car_one_stop.common.external.ocm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc2006group5.car_one_stop.dto.map.EVChargerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches EV charger locations from Open Charge Map (OCM).
 * OCM data is static (no real-time slot availability), so every station
 * returned has {@code hasLiveStatus = false}.
 *
 * Cache TTL: 1 hour (OCM data changes rarely).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcmClient {

    private static final String OCM_URL =
            "https://api.openchargemap.io/v3/poi/?output=json&countrycode=SG&maxresults=1000&compact=true";

    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+\\.?\\d*)");
    /** Used when OCM provides no pricing data. Median Singapore public charging rate. */
    private static final String DEFAULT_PRICE_PER_KWH = "0.45";
    private static final long CACHE_TTL_MS = 60 * 60 * 1000L; // 1 hour

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ocm.api.key:}")
    private String apiKey;

    private volatile List<EVChargerDto> cache = List.of();
    private volatile long lastRefreshMs = 0L;

    public List<EVChargerDto> fetchAllEVChargers() {
        long now = System.currentTimeMillis();
        if (!cache.isEmpty() && (now - lastRefreshMs) < CACHE_TTL_MS) {
            return cache;
        }
        List<EVChargerDto> refreshed = fetchFromOcm();
        if (!refreshed.isEmpty()) {
            cache = List.copyOf(refreshed);
            lastRefreshMs = System.currentTimeMillis();
        } else if (!cache.isEmpty()) {
            log.warn("OCM refresh failed; serving stale cached data ({} stations)", cache.size());
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<EVChargerDto> fetchFromOcm() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            if (apiKey != null && !apiKey.isBlank()) {
                headers.set("X-Openchargemap-Api-Key", apiKey);
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    OCM_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("OCM returned empty body");
                return List.of();
            }

            List<Map<String, Object>> stations = objectMapper.readValue(body, List.class);
            log.info("OCM: fetched {} stations", stations.size());

            return stations.stream()
                    .map(this::toDto)
                    .filter(dto -> dto.getLatitude() != 0 && dto.getLongitude() != 0)
                    .toList();

        } catch (Exception e) {
            log.warn("OCM fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private EVChargerDto toDto(Map<String, Object> station) {
        Map<String, Object> addr = station.get("AddressInfo") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        double lat = getDouble(addr, "Latitude");
        double lng = getDouble(addr, "Longitude");
        String name = getString(addr, "Title");
        String address = getString(addr, "AddressLine1");
        String postal = getString(addr, "Postcode");
        String usageCost = getString(station, "UsageCost");

        // Parse connections → LTA-compatible chargingPoints structure
        List<Object> chargingPoints = new ArrayList<>();
        Object connRaw = station.get("Connections");
        if (connRaw instanceof List<?> connections) {
            for (Object connObj : connections) {
                if (!(connObj instanceof Map<?, ?> conn)) continue;

                String plugType = "";
                Object ct = conn.get("ConnectionType");
                if (ct instanceof Map<?, ?> ctMap) {
                    Object title = ctMap.get("Title");
                    if (title != null) plugType = title.toString();
                }

                double powerKw = getDouble((Map<String, Object>) conn, "PowerKW");

                // Try to extract price per kWh from the cost string; fall back to default
                String priceStr = extractPrice(usageCost);
                if (priceStr.isBlank()) priceStr = DEFAULT_PRICE_PER_KWH;

                Map<String, Object> plugTypeMap = new LinkedHashMap<>();
                plugTypeMap.put("plugType", plugType);
                plugTypeMap.put("powerRating", String.valueOf(powerKw));
                plugTypeMap.put("price", priceStr);
                plugTypeMap.put("evIds", List.of()); // no booking IDs from OCM

                Map<String, Object> cpMap = new LinkedHashMap<>();
                // "unknown" — not "1" or "0" — signals no real-time data
                cpMap.put("status", "unknown");
                cpMap.put("plugTypes", List.of(plugTypeMap));
                chargingPoints.add(cpMap);
            }
        }

        return EVChargerDto.builder()
                .name(name)
                .address(address)
                .latitude(lat)
                .longitude(lng)
                .postalCode(postal)
                .chargingPoints(chargingPoints)
                .hasLiveStatus(false)
                .build();
    }

    /** Extract the first decimal number from a free-text price string, e.g. "S$0.45/kWh" → "0.45" */
    private String extractPrice(String text) {
        if (text == null || text.isBlank()) return "";
        Matcher m = PRICE_PATTERN.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0.0;
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
