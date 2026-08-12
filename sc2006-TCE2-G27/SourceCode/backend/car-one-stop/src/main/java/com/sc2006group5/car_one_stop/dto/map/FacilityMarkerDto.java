package com.sc2006group5.car_one_stop.dto.map;

import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacilityMarkerDto {
    private Long id;
    private FacilityType type;
    private String name;
    private String address;
    private double lat;
    private double lng;
}