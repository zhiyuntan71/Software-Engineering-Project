package com.sc2006group5.car_one_stop.dto.map;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeocodeResponseDto {
    private String formattedAddress;
    private double lat;
    private double lng;
}

// user search will be converted to lat and long for the google api