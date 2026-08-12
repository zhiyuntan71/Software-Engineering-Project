package com.sc2006group5.car_one_stop.dto.map;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarparkDto {
    private String carParkID;
    private String development;
    private double latitude;
    private double longitude;
    private int availableLots;
    private String agency;
}