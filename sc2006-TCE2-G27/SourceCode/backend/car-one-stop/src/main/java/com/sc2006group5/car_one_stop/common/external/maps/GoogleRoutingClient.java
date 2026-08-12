package com.sc2006group5.car_one_stop.common.external.maps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("googlemaps")
@RequiredArgsConstructor
@Slf4j
public class GoogleRoutingClient implements RoutingClient {

    private static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    @Value("${google.maps.api-key:}")
    private String apiKey;

    @Override
    public Optional<RoutePlanResult> plan(double originLat, double originLng, double destLat, double destLng) {
        log.debug("GoogleRoutingClient.plan called origin=({}, {}), dest=({}, {})",
                originLat, originLng, destLat, destLng);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GoogleRoutingClient: api key is missing or blank");
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline");

        Map<String, Object> body = Map.of(
                "origin", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", originLat,
                                        "longitude", originLng
                                )
                        )
                ),
                "destination", Map.of(
                        "location", Map.of(
                                "latLng", Map.of(
                                        "latitude", destLat,
                                        "longitude", destLng
                                )
                        )
                ),
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_AWARE"
        );

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    ROUTES_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            log.debug("GoogleRoutingClient: routes API status={}", response.getStatusCode().value());

            String rawBody = response.getBody();
            if (rawBody == null || rawBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = OBJECT_MAPPER.readTree(rawBody);
            JsonNode route = root.path("routes").path(0);
            if (route.isMissingNode()) {
                return Optional.empty();
            }

            int distanceMeters = route.path("distanceMeters").asInt(0);
            int durationSeconds = parseDurationSeconds(route.path("duration").asText("0s"));
            String polyline = route.path("polyline").path("encodedPolyline").asText("");

            return Optional.of(
                    RoutePlanResult.builder()
                            .distanceMeters(distanceMeters)
                            .durationSeconds(durationSeconds)
                            .polyline(polyline)
                            .build()
            );
        } catch (Exception e) {
            log.error("GoogleRoutingClient route call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private int parseDurationSeconds(String durationValue) {
        if (durationValue == null || durationValue.isBlank()) {
            return 0;
        }

        String numeric = durationValue.endsWith("s")
                ? durationValue.substring(0, durationValue.length() - 1)
                : durationValue;

        try {
            return BigDecimal.valueOf(Double.parseDouble(numeric)).intValue();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
