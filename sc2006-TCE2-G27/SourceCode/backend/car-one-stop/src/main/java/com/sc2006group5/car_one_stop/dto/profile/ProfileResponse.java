package com.sc2006group5.car_one_stop.dto.profile;

import com.sc2006group5.car_one_stop.enums.auth.CarModel;
import com.sc2006group5.car_one_stop.enums.auth.CarType;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;

import java.math.BigDecimal;

public record ProfileResponse(
        String username,
        String email,
        CarType carType,
        CarModel carModel,
        ChargingType chargingType
) {}
