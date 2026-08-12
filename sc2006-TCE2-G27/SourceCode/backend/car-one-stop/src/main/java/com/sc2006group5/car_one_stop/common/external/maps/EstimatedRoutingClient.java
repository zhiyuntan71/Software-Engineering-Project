package com.sc2006group5.car_one_stop.common.external.maps;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile("!googlemaps")
public class EstimatedRoutingClient implements RoutingClient {

    // 40 km/h in meters/second.
    private static final double AVG_DRIVE_SPEED_MPS = 11.11;

    @Override
    public Optional<RoutePlanResult> plan(double originLat, double originLng, double destLat, double destLng) {
        double meters = haversineMeters(originLat, originLng, destLat, destLng);
        int distanceMeters = (int) Math.round(meters);
        int durationSeconds = Math.max(1, (int) Math.round(meters / AVG_DRIVE_SPEED_MPS));

        return Optional.of(
                RoutePlanResult.builder()
                        .distanceMeters(distanceMeters)
                        .durationSeconds(durationSeconds)
                        .polyline("")
                        .build()
        );
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double radius = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * radius * Math.asin(Math.sqrt(a));
    }
}
