package com.sc2006group5.car_one_stop.common.external.maps;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EstimatedRoutingClientTest {

    private final EstimatedRoutingClient client = new EstimatedRoutingClient();

    @Test
    void plan_returnsPositiveDistanceAndDuration_forDifferentPoints() {
        Optional<RoutePlanResult> result = client.plan(1.3521, 103.8198, 1.3000, 103.8000);

        assertTrue(result.isPresent());
        assertTrue(result.get().getDistanceMeters() > 0);
        assertTrue(result.get().getDurationSeconds() > 0);
    }

    @Test
    void plan_returnsAtLeastOneSecond_forSamePoint() {
        Optional<RoutePlanResult> result = client.plan(1.3521, 103.8198, 1.3521, 103.8198);

        assertTrue(result.isPresent());
        assertTrue(result.get().getDistanceMeters() >= 0);
        assertTrue(result.get().getDurationSeconds() >= 1);
    }
}
