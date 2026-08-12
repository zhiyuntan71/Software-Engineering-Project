package com.sc2006group5.car_one_stop.dto.map;

import com.sc2006group5.car_one_stop.entity.map.UserPreference;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class UserPreferenceDto {

    Long id;
    BigDecimal maxHourlyRate;
    Integer minAvailableLots;
    Boolean requireSheltered;
    double weightDistance;
    double weightPrice;
    double weightAvailability;
    int parkingDurationHours;
    int evParkingDurationHours;

    public static UserPreferenceDto from(UserPreference pref) {
        return new UserPreferenceDto(
                pref.getId(),
                pref.getMaxHourlyRate(),
                pref.getMinAvailableLots(),
                pref.getRequireSheltered(),
                pref.getWeightDistance(),
                pref.getWeightPrice(),
                pref.getWeightAvailability(),
                normalizeDurationHours(pref.getParkingDurationHours()),
                normalizeDurationHours(pref.getEvParkingDurationHours())
        );
    }

    private static int normalizeDurationHours(Integer durationHours) {
        if (durationHours == null || durationHours <= 1) {
            return 1;
        }
        if (durationHours >= 4) {
            return 4;
        }
        return durationHours;
    }
}
