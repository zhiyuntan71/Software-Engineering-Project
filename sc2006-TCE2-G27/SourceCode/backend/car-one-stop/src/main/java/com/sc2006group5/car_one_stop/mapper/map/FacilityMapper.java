package com.sc2006group5.car_one_stop.mapper.map;

import com.sc2006group5.car_one_stop.dto.map.FacilityMarkerDto;
import com.sc2006group5.car_one_stop.entity.map.Facility;

public class FacilityMapper {

    private FacilityMapper() {}

    public static FacilityMarkerDto toMarkerDto(Facility f) {
        return FacilityMarkerDto.builder()
                .id(f.getId())
                .type(f.getType())
                .name(f.getName())
                .address(f.getAddress())
                .lat(f.getLatitude())
                .lng(f.getLongitude())
                .build();
    }
}