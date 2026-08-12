package com.sc2006group5.car_one_stop.common.external.maps;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoutePlanResult {
    private int distanceMeters;
    private int durationSeconds;
    private String polyline;
}