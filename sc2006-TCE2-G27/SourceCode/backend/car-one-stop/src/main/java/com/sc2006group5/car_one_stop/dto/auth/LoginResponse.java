package com.sc2006group5.car_one_stop.dto.auth;

import com.sc2006group5.car_one_stop.enums.auth.CarModel;
import com.sc2006group5.car_one_stop.enums.auth.CarType;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;

public record LoginResponse(
        Long userId,
        String username,
        String email,
        String token,
        CarType carType,
        CarModel carModel,
        ChargingType chargingType)
{}
