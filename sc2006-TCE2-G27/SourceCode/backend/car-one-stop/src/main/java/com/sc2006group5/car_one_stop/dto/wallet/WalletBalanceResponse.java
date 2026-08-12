package com.sc2006group5.car_one_stop.dto.wallet;

import java.math.BigDecimal;

public record WalletBalanceResponse(
        BigDecimal balance
) {}