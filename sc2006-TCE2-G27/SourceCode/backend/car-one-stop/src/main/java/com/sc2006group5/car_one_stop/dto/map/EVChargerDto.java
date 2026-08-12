package com.sc2006group5.car_one_stop.dto.map;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EVChargerDto {
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private String postalCode;
    private List<Object> chargingPoints;
    /** true = LTA (real-time status), false = OCM (no live availability data) */
    @Builder.Default
    private boolean hasLiveStatus = true;
}