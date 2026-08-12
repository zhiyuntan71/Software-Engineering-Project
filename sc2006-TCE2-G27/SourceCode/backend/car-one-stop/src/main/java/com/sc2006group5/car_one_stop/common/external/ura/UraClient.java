package com.sc2006group5.car_one_stop.common.external.ura;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sc2006group5.car_one_stop.common.geo.Svy21Converter;
import com.sc2006group5.car_one_stop.dto.map.CarparkAvailabilityDto;
import com.sc2006group5.car_one_stop.dto.map.UraCarParkDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UraClient {

    private static final long TOKEN_TTL_MS    = 23L * 60 * 60 * 1000; // 23 hours
    private static final long CARPARKS_TTL_MS = 24L * 60 * 60 * 1000; // 24 hours

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    @Value("${ura.api.access-key}")
    private String accessKey;

    @Value("${ura.api.base-url:https://eservice.ura.gov.sg/uraDataService}")
    private String baseUrl;

    private volatile String cachedToken;
    private volatile long tokenFetchedAt = 0L;

    private volatile List<UraCarParkDto> cachedCarParks = Collections.emptyList();
    private volatile long carParksFetchedAt = 0L;

    private volatile List<CarparkAvailabilityDto> cachedAvailability = Collections.emptyList();
    private volatile long availabilityFetchedAt = 0L;
    private static final long AVAILABILITY_TTL_MS = 2L * 60 * 1000; // 2 minutes

    // ── Startup validation ─────────────────────────────────────────────────

    @PostConstruct
    void validateConfig() {
        if (accessKey == null || accessKey.isBlank()) {
            log.error("URA access key (ura.api.access-key) is MISSING or blank — " +
                      "all URA carpark data will be unavailable. " +
                      "Add it to application-local.yml.");
        } else {
            log.info("URA config OK — base-url={}, AccessKey={}***",
                     baseUrl, accessKey.substring(0, Math.min(8, accessKey.length())));
        }
    }

    // ── Token ──────────────────────────────────────────────────────────────

    public String getToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && !cachedToken.isBlank() && (now - tokenFetchedAt) < TOKEN_TTL_MS) {
            return cachedToken;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("AccessKey", accessKey);

            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/insertNewToken/v1", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            String body = resp.getBody();
            if (body != null && !body.trim().startsWith("{")) {
                log.warn("URA token endpoint returned non-JSON (HTTP {}). " +
                         "The API is likely only accessible from whitelisted server IPs. " +
                         "URA carpark data will be unavailable in this environment.",
                         resp.getStatusCode().value());
                return null;
            }
            Map<String, Object> json;
            try {
                json = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("URA token response is not valid JSON: {}", e.getMessage());
                return null;
            }
            Object result = json.get("Result");
            if (result == null || result.toString().isBlank()) return null;

            cachedToken = result.toString().trim();
            tokenFetchedAt = System.currentTimeMillis();
            log.info("URA daily token refreshed");
            return cachedToken;
        } catch (Exception e) {
            log.error("Failed to fetch URA daily token: {}", e.getMessage());
            return null;
        }
    }

    // ── Carpark details ────────────────────────────────────────────────────

    public List<UraCarParkDto> fetchAllCarParks() {
        long now = System.currentTimeMillis();
        if (!cachedCarParks.isEmpty() && (now - carParksFetchedAt) < CARPARKS_TTL_MS) {
            return cachedCarParks;
        }

        String token = getToken();
        if (token == null) {
            log.warn("No URA token available — skipping carpark fetch");
            return cachedCarParks.isEmpty() ? Collections.emptyList() : cachedCarParks;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("AccessKey", accessKey);
            headers.set("Token", token);

            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/invokeUraDS/v1?service=Car_Park_Details",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);

            List<Map<String, Object>> raw = extractResultList(resp.getBody(), "Car_Park_Details");
            if (raw == null) return Collections.emptyList();

            // One row per (ppCode, vehCat). Keep Car only, deduplicate by ppCode (first wins).
            Map<String, UraCarParkDto> byCode = new LinkedHashMap<>();
            for (Map<String, Object> r : raw) {
                if (!"Car".equalsIgnoreCase(str(r, "vehCat"))) continue;
                String ppCode = str(r, "ppCode");
                if (ppCode.isEmpty() || byCode.containsKey(ppCode)) continue;
                UraCarParkDto dto = parseCarPark(r);
                if (dto != null) byCode.put(ppCode, dto);
            }

            List<UraCarParkDto> result = new ArrayList<>(byCode.values());
            log.info("Loaded {} URA carparks (Car category)", result.size());
            cachedCarParks = Collections.unmodifiableList(result);
            carParksFetchedAt = System.currentTimeMillis();
            return cachedCarParks;

        } catch (Exception e) {
            log.error("Failed to fetch URA carpark details: {}", e.getMessage());
            return cachedCarParks;
        }
    }

    // ── Availability ───────────────────────────────────────────────────────

    public List<CarparkAvailabilityDto> fetchAvailability() {
        long now = System.currentTimeMillis();
        if (!cachedAvailability.isEmpty() && (now - availabilityFetchedAt) < AVAILABILITY_TTL_MS) {
            return cachedAvailability;
        }

        String token = getToken();
        if (token == null) {
            log.warn("No URA token — skipping availability fetch");
            return cachedAvailability;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("AccessKey", accessKey);
            headers.set("Token", token);

            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/invokeUraDS/v1?service=Car_Park_Availability",
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);

            List<Map<String, Object>> raw = extractResultList(resp.getBody(), "Car_Park_Availability");
            if (raw == null) return cachedAvailability;

            List<CarparkAvailabilityDto> result = new ArrayList<>();
            for (Map<String, Object> r : raw) {
                // Filter to cars only — URA uses lotType "C" for cars
                if (!"C".equalsIgnoreCase(str(r, "lotType"))) continue;

                // Availability uses "carparkNo"; Details uses "ppCode" — check both
                String id = str(r, "carparkNo");
                if (id.isEmpty()) id = str(r, "ppCode");
                if (id.isEmpty()) continue;

                String lotsStr = str(r, "lotsAvailable");
                try {
                    result.add(new CarparkAvailabilityDto(id, Integer.parseInt(lotsStr)));
                } catch (NumberFormatException ignored) {}
            }

            log.info("Loaded URA availability: {} car lots entries", result.size());
            cachedAvailability = Collections.unmodifiableList(result);
            availabilityFetchedAt = System.currentTimeMillis();
            return cachedAvailability;

        } catch (Exception e) {
            log.error("Failed to fetch URA carpark availability: {}", e.getMessage());
            return cachedAvailability;
        }
    }

    //Shared helpers
    private List<Map<String, Object>> extractResultList(String body, String source) {
        if (body == null || body.isBlank()) return null;
        Map<String, Object> json;
        try {
            json = OBJECT_MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("URA {}: could not parse response as JSON", source);
            return null;
        }
        Object resultObj = json.get("Result");
        if (!(resultObj instanceof List<?>)) {
            log.warn("URA {}: Result is not a list — Status={}", source, json.get("Status"));
            return null;
        }
        return OBJECT_MAPPER.convertValue(resultObj, new TypeReference<List<Map<String, Object>>>() {});
    }

    //Parsing

    private UraCarParkDto parseCarPark(Map<String, Object> r) {
        String ppCode = str(r, "ppCode");
        String ppName = str(r, "ppName");
        if (ppCode.isEmpty()) return null;


        double lat = 0, lng = 0;
        Object geomObj = r.get("geometries");
        if (geomObj instanceof List<?> geoms && !((List<?>) geoms).isEmpty()) {
            Object first = ((List<?>) geoms).get(0);
            if (first instanceof Map<?, ?> gm) {
                String coords = str((Map<String, Object>) gm, "coordinates");
                String[] parts = coords.split(",");
                if (parts.length == 2) {
                    try {
                        double x = Double.parseDouble(parts[0].trim());
                        double y = Double.parseDouble(parts[1].trim());
                        if (x > 0 && y > 0) {
                            double[] ll = Svy21Converter.fromEastingNorthing(x, y);
                            lat = ll[0];
                            lng = ll[1];
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (lat == 0 || lng == 0) return null;

        // Hourly rate derived from weekday rate + period
        BigDecimal hourlyRate = parseHourlyRate(str(r, "weekdayRate"), str(r, "weekdayMin"));
        if (hourlyRate == null) return null; // season-only or unparseable — skip

        // Opening hours
        LocalTime startTime = parseUraTime(str(r, "startTime"));
        LocalTime endTime   = parseUraTime(str(r, "endTime"));

        return UraCarParkDto.builder()
                .ppCode(ppCode)
                .ppName(ppName.isEmpty() ? ppCode : ppName)
                .latitude(lat)
                .longitude(lng)
                .hourlyRate(hourlyRate)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }


    static BigDecimal parseHourlyRate(String rateStr, String minStr) {
        if (rateStr == null || rateStr.isBlank()) return null;
        String r = rateStr.trim();

        if (r.toLowerCase().contains("season") || r.toLowerCase().contains("free")) return null;

        if (r.startsWith("$")) r = r.substring(1);
        double rate;
        try {
            rate = Double.parseDouble(r);
        } catch (NumberFormatException e) {
            return null; // complex format — skip
        }

        // Derive the period in hours from weekdayMin ("30 mins",)
        double periodHours = 0.5; // default: 30-min blocks
        if (minStr != null && !minStr.isBlank()) {
            String m = minStr.trim().toLowerCase();
            if (m.contains("hr") || m.contains("hour") || m.startsWith("1st")) {
                periodHours = 1.0;
            } else {
                String digits = m.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        int mins = Integer.parseInt(digits);
                        if (mins > 0) periodHours = mins / 60.0;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // hourlyRate = rate per period ÷ periodHours
        long cents = Math.round(rate / periodHours * 100);
        return BigDecimal.valueOf(cents).movePointLeft(2);
    }

    // Parses URA time strings like "07.00 AM", "10.30 PM", "11.00 PM".
    static LocalTime parseUraTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        String s = timeStr.trim().toUpperCase();

        boolean pm = s.endsWith("PM");
        boolean am = s.endsWith("AM");
        if (!pm && !am) return null;

        // Strip "AM"/"PM" and the space before it
        String timePart = s.substring(0, s.length() - 2).trim();
        String[] parts = timePart.split("\\.");
        if (parts.length != 2) return null;

        try {
            int hour   = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (pm && hour != 12) hour += 12;
            if (am && hour == 12) hour = 0;
            return LocalTime.of(hour, minute);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private static double[] svy21ToLatLon(double x, double y) {
        return Svy21Converter.fromEastingNorthing(x, y);
    }
}
