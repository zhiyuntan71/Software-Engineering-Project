package com.sc2006group5.car_one_stop.dto.profile;

import com.sc2006group5.car_one_stop.enums.auth.CarModel;
import com.sc2006group5.car_one_stop.enums.auth.CarType;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        String username,
        CarType carType,
        CarModel carModel,
        ChargingType chargingType
) {}