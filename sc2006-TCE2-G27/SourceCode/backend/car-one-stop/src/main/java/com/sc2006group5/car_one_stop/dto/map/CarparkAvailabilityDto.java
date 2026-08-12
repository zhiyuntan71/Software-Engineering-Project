package com.sc2006group5.car_one_stop.dto.map;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CarparkAvailabilityDto {
    private String carparkNumber;
    private int lotsAvailable;
}