package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.BreakoutDay;
import com.qullamaggie.tradingsystem.backtest.DayStatus;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BreakoutDayTest {

    private BreakoutDay day(DayStatus status, Set<BreakoutCriterion> failed) {
        return new BreakoutDay("AAPL", LocalDate.of(2021, 3, 15), RegimeStatus.RISK_ON, status, failed);
    }

    @Test
    void evaluatedWithNoFailuresIsSignal() {
        BreakoutDay d = day(DayStatus.EVALUATED, EnumSet.noneOf(BreakoutCriterion.class));
        assertTrue(d.isSignal());
        assertFalse(d.isNearMiss());
    }

    @Test
    void evaluatedWithOneFailureIsNearMiss() {
        BreakoutDay d = day(DayStatus.EVALUATED, EnumSet.of(BreakoutCriterion.PULLBACK));
        assertTrue(d.isNearMiss());
        assertFalse(d.isSignal());
    }

    @Test
    void evaluatedWithTwoFailuresIsNeither() {
        BreakoutDay d = day(DayStatus.EVALUATED,
                EnumSet.of(BreakoutCriterion.PULLBACK, BreakoutCriterion.PRIOR_MOVE));
        assertFalse(d.isSignal());
        assertFalse(d.isNearMiss());
    }

    @Test
    void dayWithoutFlagIsNeverSignalEvenThoughNothingFailed() {
        BreakoutDay d = day(DayStatus.NO_FLAG, EnumSet.noneOf(BreakoutCriterion.class));
        assertFalse(d.isSignal());
        assertFalse(d.isNearMiss());
    }

    @Test
    void dayOutsideUniverseIsNeverSignal() {
        BreakoutDay d = day(DayStatus.OUTSIDE_UNIVERSE, EnumSet.noneOf(BreakoutCriterion.class));
        assertFalse(d.isSignal());
    }
}