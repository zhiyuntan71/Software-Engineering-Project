package com.sc2006group5.car_one_stop.dto.map;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UraCarParkDto {
    private String ppCode;       // URA carpark ID — used as carparkNo in Facility
    private String ppName;
    private double latitude;
    private double longitude;
    private BigDecimal hourlyRate;  // derived from weekdayRate / weekdayMin
    private LocalTime startTime;
    private LocalTime endTime;
}
