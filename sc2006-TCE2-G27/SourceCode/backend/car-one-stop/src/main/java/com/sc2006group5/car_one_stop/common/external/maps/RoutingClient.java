package com.sc2006group5.car_one_stop.common.external.maps;

import java.util.Optional;

public interface RoutingClient {
    Optional<RoutePlanResult> plan(double originLat, double originLng, double destLat, double destLng);
}