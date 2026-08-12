package com.sc2006group5.car_one_stop.dto.map;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarparkInfoDto {
    private String carparkNo;
    private String address;
    private double latitude;
    private double longitude;
    private String shortTermParking; // "WHOLE DAY", "7AM-10.30PM", "NO", etc.
    private String freeParkingInfo;  // e.g. "SUN & PH FR 7AM-10.30PM", "NO", or blank
}