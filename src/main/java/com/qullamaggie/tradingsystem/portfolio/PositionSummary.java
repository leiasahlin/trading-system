package com.qullamaggie.tradingsystem.portfolio;

import java.math.BigDecimal;

public record PositionSummary(
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        int remainingShares,
        BigDecimal realizedPnL,
        BigDecimal unrealizedPnL,
        BigDecimal currentR
) {}
