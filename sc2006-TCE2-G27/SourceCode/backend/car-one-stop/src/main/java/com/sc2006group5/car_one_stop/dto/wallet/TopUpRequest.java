package com.sc2006group5.car_one_stop.dto.wallet;

import com.sc2006group5.car_one_stop.enums.wallet.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record TopUpRequest(
        @NotNull Integer amount,
        @NotNull PaymentMethod paymentMethod
) {}
