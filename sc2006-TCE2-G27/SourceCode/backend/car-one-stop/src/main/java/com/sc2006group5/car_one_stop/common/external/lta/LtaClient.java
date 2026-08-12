package com.sc2006group5.car_one_stop.common.external.lta;

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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LtaClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lta.api.key}")
    private String apiKey;

    @Value("${lta.api.base-url}")
    private String baseUrl;

    @Value("${lta.ev.cache-ttl-ms:60000}")
    private long evCacheTtlMs;

    @Value("${lta.ev.min-refresh-interval-ms:1500}")
    private long minRefreshIntervalMs;

    private final Object evCacheLock = new Object();
    private volatile List<EVChargerDto> cachedEvChargers = List.of();
    private volatile long lastSuccessfulRefreshMs = 0L;
    private volatile long lastRefreshAttemptMs = 0L;

    public String fetchEVBatchLink() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("AccountKey", apiKey);
        headers.set("Accept", "application/json");

        String url = baseUrl + "/EVCBatch";
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                log.info("Calling LTA EVCBatch: {}", url);
                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        Map.class
                );

                log.info("EVCBatch status: {}", response.getStatusCode());

                if (response.getBody() == null) {
                    log.warn("EVCBatch returned null body");
                    return null;
                }

                Object valueObj = response.getBody().get("value");
                if (!(valueObj instanceof List<?> value)) {
                    log.warn("EVCBatch 'value' is not a list: {}", valueObj);
                    return null;
                }

                if (value.isEmpty() || !(value.get(0) instanceof Map<?, ?> firstEntry)) {
                    log.warn("EVCBatch 'value' list is empty or malformed");
                    return null;
                }

                Object linkObj = firstEntry.get("Link");
                if (linkObj == null) {
                    log.warn("EVCBatch response did not contain Link");
                    return null;
                }

                String link = linkObj.toString();
                log.info("EV Batch download link obtained");
                return link;
            } catch (HttpStatusCodeException e) {
                String responseBody = e.getResponseBodyAsString();
                boolean isSpikeArrest = responseBody != null && responseBody.contains("SpikeArrestViolation");
                if (attempt < 2 && isSpikeArrest) {
                    log.warn("LTA EVCBatch spike-arrested; retrying once after backoff.");
                    sleepQuietly(1100);
                    continue;
                }
                log.warn("Failed to fetch EV batch link: status={} body={}",
                        e.getStatusCode(), responseBody);
                return null;
            } catch (Exception e) {
                log.warn("Failed to fetch EV batch link: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    public List<EVChargerDto> fetchAllEVChargers() {
        long now = System.currentTimeMillis();
        if (isCacheFresh(now)) {
            return cachedEvChargers;
        }

        synchronized (evCacheLock) {
            now = System.currentTimeMillis();
            if (isCacheFresh(now)) {
                return cachedEvChargers;
            }

            if ((now - lastRefreshAttemptMs) < minRefreshIntervalMs) {
                log.debug("Skipping LTA EV refresh due to min-refresh interval; serving cached data.");
                return cachedEvChargers;
            }

            lastRefreshAttemptMs = now;
            List<EVChargerDto> refreshed = fetchAllEVChargersFromLta();
            if (!refreshed.isEmpty()) {
                cachedEvChargers = List.copyOf(refreshed);
                lastSuccessfulRefreshMs = System.currentTimeMillis();
                return cachedEvChargers;
            }

            if (!cachedEvChargers.isEmpty()) {
                log.warn("Using stale cached EV data after LTA refresh failure.");
                return cachedEvChargers;
            }

            return List.of();
        }
    }

    private List<EVChargerDto> fetchAllEVChargersFromLta() {
        String link = fetchEVBatchLink();
        if (link == null) {
            log.warn("No EV batch link available");
            return List.of();
        }

        try {
            URI uri = URI.create(link);

            ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class
            );

            log.info("Batch file status: {}", response.getStatusCode());

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("Batch file returned empty body");
                return List.of();
            }

            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            log.info("Batch file keys: {}", parsed.keySet());

            List<Map<String, Object>> evData;
            if (parsed.containsKey("evLocationsData")) {
                evData = (List<Map<String, Object>>) parsed.get("evLocationsData");
            } else if (parsed.containsKey("value")) {
                evData = (List<Map<String, Object>>) parsed.get("value");
            } else {
                log.warn("Unknown batch structure. Keys: {}", parsed.keySet());
                return List.of();
            }

            if (evData == null || evData.isEmpty()) {
                log.warn("EV data list is null or empty");
                return List.of();
            }

            log.info("Found {} EV charger locations", evData.size());

            return evData.stream()
                    .map(this::toEvChargerDto)
                    .toList();
        } catch (HttpStatusCodeException e) {
            log.warn("Failed to fetch/parse EV batch file: status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch/parse EV batch file: {}", e.getMessage());
            return List.of();
        }
    }

    private EVChargerDto toEvChargerDto(Map<String, Object> ev) {
        Object cpRaw = ev.get("chargingPoints");
        List<Object> chargingPoints = cpRaw instanceof List<?> list
                ? (List<Object>) list
                : Collections.emptyList();

        return EVChargerDto.builder()
                .name(getString(ev, "name"))
                .address(getString(ev, "address"))
                .latitude(getDouble(ev, "latitude"))
                .longitude(getDouble(ev, "longtitude"))
                .postalCode(getString(ev, "postalCode"))
                .chargingPoints(chargingPoints)
                .hasLiveStatus(true)
                .build();
    }

    private boolean isCacheFresh(long nowMs) {
        return !cachedEvChargers.isEmpty() && (nowMs - lastSuccessfulRefreshMs) < evCacheTtlMs;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
