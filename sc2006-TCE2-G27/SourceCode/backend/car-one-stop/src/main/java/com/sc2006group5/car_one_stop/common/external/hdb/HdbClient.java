package com.sc2006group5.car_one_stop.common.external.hdb;

import com.sc2006group5.car_one_stop.common.geo.Svy21Converter;
import com.sc2006group5.car_one_stop.dto.map.CarparkAvailabilityDto;
import com.sc2006group5.car_one_stop.dto.map.CarparkDto;
import com.sc2006group5.car_one_stop.dto.map.CarparkInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class HdbClient {

    private record FetchResult(List<CarparkDto> coords, List<CarparkInfoDto> info) {}

    private final RestTemplate restTemplate;

    @Value("${hdb.carpark.coords.url}")
    private String coordsUrl;

    @Value("${hdb.carpark.availability.url}")
    private String availabilityUrl;

    @Value("${hdb.api.key:}")
    private String hdbApiKey;

    @Value("${hdb.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${hdb.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${hdb.cache.coords-ttl-ms:86400000}")
    private long coordsTtlMs;

    @Value("${hdb.cache.availability-ttl-ms:30000}")
    private long availabilityTtlMs;

    private final Object hdbDataLock = new Object();
    private volatile List<CarparkDto> cachedCoords = Collections.emptyList();
    private volatile long cachedCoordsAt = 0L;
    private volatile List<CarparkInfoDto> cachedCarparkInfo = Collections.emptyList();
    private volatile long cachedCarparkInfoAt = 0L;

    private final Object availabilityLock = new Object();
    private volatile List<CarparkAvailabilityDto> cachedAvailability = Collections.emptyList();
    private volatile long cachedAvailabilityAt = 0L;

    // ── Public API ────────────────────────────────────────────────────────

    public List<CarparkDto> fetchAllCarparkCoords() {
        long now = System.currentTimeMillis();
        if (!cachedCoords.isEmpty() && (now - cachedCoordsAt) < coordsTtlMs) return cachedCoords;

        synchronized (hdbDataLock) {
            now = System.currentTimeMillis();
            if (!cachedCoords.isEmpty() && (now - cachedCoordsAt) < coordsTtlMs) return cachedCoords;
            refreshHdbCache();
            return cachedCoords;
        }
    }

    public List<CarparkInfoDto> fetchAllCarparkInfo() {
        long now = System.currentTimeMillis();
        if (!cachedCarparkInfo.isEmpty() && (now - cachedCarparkInfoAt) < coordsTtlMs) return cachedCarparkInfo;

        synchronized (hdbDataLock) {
            now = System.currentTimeMillis();
            if (!cachedCarparkInfo.isEmpty() && (now - cachedCarparkInfoAt) < coordsTtlMs) return cachedCarparkInfo;
            refreshHdbCache();
            return cachedCarparkInfo;
        }
    }

    public List<CarparkAvailabilityDto> fetchAvailability() {
        long now = System.currentTimeMillis();
        if (!cachedAvailability.isEmpty() && (now - cachedAvailabilityAt) < availabilityTtlMs) return cachedAvailability;

        synchronized (availabilityLock) {
            now = System.currentTimeMillis();
            if (!cachedAvailability.isEmpty() && (now - cachedAvailabilityAt) < availabilityTtlMs) return cachedAvailability;

            List<CarparkAvailabilityDto> fresh = fetchAvailabilityWithRetry();
            if (!fresh.isEmpty()) {
                cachedAvailability = Collections.unmodifiableList(new ArrayList<>(fresh));
                cachedAvailabilityAt = System.currentTimeMillis();
                return cachedAvailability;
            }

            if (!cachedAvailability.isEmpty()) {
                log.warn("Using stale cached carpark availability due to upstream fetch failure (size={})", cachedAvailability.size());
                return cachedAvailability;
            }

            return Collections.emptyList();
        }
    }

    // ── Cache refresh ─────────────────────────────────────────────────────

    private void refreshHdbCache() {
        FetchResult fresh = fetchAllCarparkInfoWithRetry();
        long now = System.currentTimeMillis();
        if (!fresh.coords().isEmpty()) {
            cachedCoords = fresh.coords();
            cachedCarparkInfo = fresh.info();
            cachedCoordsAt = now;
            cachedCarparkInfoAt = now;
        } else {
            if (!cachedCoords.isEmpty())
                log.warn("Using stale HDB cache due to upstream fetch failure (size={})", cachedCoords.size());
        }
    }

    // ── Fetch with retry ──────────────────────────────────────────────────

    private FetchResult fetchAllCarparkInfoWithRetry() {
        sleepMs(500); // brief pause before first request to avoid hitting rate limit on startup
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Map response = restTemplate.exchange(
                        coordsUrl, HttpMethod.GET, apiKeyHeaders(), Map.class).getBody();
                Map result = (Map) response.get("result");
                List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
                if (records == null) return new FetchResult(Collections.emptyList(), Collections.emptyList());

                List<CarparkDto> coords = new ArrayList<>();
                List<CarparkInfoDto> info = new ArrayList<>();

                for (Map<String, Object> r : records) {
                    String id = getString(r, "car_park_no");
                    String xStr = getString(r, "x_coord");
                    String yStr = getString(r, "y_coord");
                    if (id.isEmpty() || xStr.isEmpty() || yStr.isEmpty()) continue;

                    double x, y;
                    try {
                        x = Double.parseDouble(xStr);
                        y = Double.parseDouble(yStr);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (x <= 0 || y <= 0) continue;

                    double[] latLon = Svy21Converter.fromEastingNorthing(x, y);
                    String address = getString(r, "address");

                    coords.add(CarparkDto.builder()
                            .carParkID(id)
                            .development(address)
                            .latitude(latLon[0])
                            .longitude(latLon[1])
                            .availableLots(-1)
                            .agency("HDB")
                            .build());

                    info.add(CarparkInfoDto.builder()
                            .carparkNo(id)
                            .address(address)
                            .latitude(latLon[0])
                            .longitude(latLon[1])
                            .shortTermParking(getString(r, "short_term_parking"))
                            .freeParkingInfo(getString(r, "free_parking"))
                            .build());
                }

                log.info("Loaded {} HDB carpark records (coords + info)", coords.size());
                return new FetchResult(
                        Collections.unmodifiableList(coords),
                        Collections.unmodifiableList(info)
                );

            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                long waitMs = status == 429
                        ? Math.min((1L << attempt) * 1000L, 30_000L)
                        : switch (attempt) { case 1 -> 5_000L; case 2 -> 15_000L; default -> 30_000L; };
                log.warn("HDB data fetch failed (attempt {}/{}, status={}) — retrying in {}s",
                        attempt, maxAttempts, status, waitMs / 1000);
                if (!isRetriableStatus(status) || attempt == maxAttempts) {
                    log.error("HDB data: all {} attempts exhausted — startup will proceed with 0 HDB records", maxAttempts);
                    break;
                }
                sleepMs(waitMs);

            } catch (ResourceAccessException e) {
                log.warn("HDB data timeout (attempt {}/{}): {}", attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("HDB data: all {} attempts exhausted — startup will proceed with 0 HDB records", maxAttempts);
                    break;
                }
                sleepWithBackoff(attempt);

            } catch (Exception e) {
                log.error("HDB data fetch failed: {} — startup will proceed with 0 HDB records", e.getMessage());
                break;
            }
        }
        return new FetchResult(Collections.emptyList(), Collections.emptyList());
    }

    private List<CarparkAvailabilityDto> fetchAvailabilityWithRetry() {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Map response = restTemplate.getForObject(availabilityUrl, Map.class);
                List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                if (items == null || items.isEmpty()) return Collections.emptyList();

                List<Map<String, Object>> carparkData =
                        (List<Map<String, Object>>) items.get(0).get("carpark_data");

                List<CarparkAvailabilityDto> result = new ArrayList<>();
                for (Map<String, Object> item : carparkData) {
                    String number = getString(item, "carpark_number");
                    List<Map<String, Object>> info =
                            (List<Map<String, Object>>) item.get("carpark_info");

                    int lots = -1;
                    if (info != null && !info.isEmpty()) {
                        String lotsStr = getString(info.get(0), "lots_available");
                        try { lots = Integer.parseInt(lotsStr); }
                        catch (NumberFormatException ignored) {}
                    }
                    result.add(new CarparkAvailabilityDto(number, lots));
                }
                return result;

            } catch (HttpStatusCodeException e) {
                log.warn("Carpark availability request failed (attempt {}/{}), status={}, body={}",
                        attempt, maxAttempts, e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
                if (!isRetriableStatus(e.getStatusCode().value()) || attempt == maxAttempts) break;
                sleepWithBackoff(attempt);

            } catch (ResourceAccessException e) {
                log.warn("Carpark availability timeout/network error (attempt {}/{}): {}",
                        attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) break;
                sleepWithBackoff(attempt);

            } catch (Exception e) {
                log.error("Failed to fetch carpark availability: {}", e.getMessage());
                break;
            }
        }
        return Collections.emptyList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private HttpEntity<?> apiKeyHeaders() {
        if (hdbApiKey == null || hdbApiKey.isBlank()) return HttpEntity.EMPTY;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", hdbApiKey);
        return new HttpEntity<>(headers);
    }

    private boolean isRetriableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private void sleepWithBackoff(int attempt) {
        sleepMs(switch (attempt) { case 1 -> 5_000L; case 2 -> 15_000L; default -> 30_000L; });
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() > 240 ? value.substring(0, 240) + "..." : value;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : "";
    }
}