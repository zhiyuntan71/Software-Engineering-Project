// src/main/java/com/sc2006group5/car_one_stop/dto/auth/RegisterRequest.java
package com.sc2006group5.car_one_stop.dto.auth;

import com.sc2006group5.car_one_stop.enums.auth.CarModel;
import com.sc2006group5.car_one_stop.enums.auth.CarType;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,72}$",
                message = "Password must include uppercase, lowercase, number, and special character"
        )
        String password,

        @NotNull CarType carType,
        @NotNull CarModel carModel,
        ChargingType chargingType

) {}
