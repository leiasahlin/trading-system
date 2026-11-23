package com.qullamaggie.tradingsystem.backtest;

import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;

import java.time.LocalDate;
import java.util.Set;

/**
 * How one stock looked on one historical day.
 * failed is empty unless status is EVALUATED.
 */
public record BreakoutDay(
        String symbol,
        LocalDate date,
        RegimeStatus regime,
        DayStatus status,
        Set<BreakoutCriterion> failed
) {
    /** All seven criteria passed. */
    public boolean isSignal() {
        return status == DayStatus.EVALUATED && failed.isEmpty();
    }

    /** Exactly one criterion failed. */
    public boolean isNearMiss() {
        return status == DayStatus.EVALUATED && failed.size() == 1;
    }
}