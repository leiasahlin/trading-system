package com.qullamaggie.tradingsystem.scanner;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;

import java.math.BigDecimal;

public record WatchlistCandidate(
        Stock stock,
        DailyPrice price,
        Indicator indicator,
        BreakoutEvaluation evaluation,
        BigDecimal distanceToBreakoutPercent
) {}
