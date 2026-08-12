package com.sc2006group5.car_one_stop.dto.wallet;

import com.sc2006group5.car_one_stop.enums.wallet.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String transactionId,
        TransactionType type,
        BigDecimal amount,
        String status,
        Instant createdAt
) {}
