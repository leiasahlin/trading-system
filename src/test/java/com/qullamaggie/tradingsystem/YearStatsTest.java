package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.BreakoutDay;
import com.qullamaggie.tradingsystem.backtest.DayStatus;
import com.qullamaggie.tradingsystem.backtest.YearStats;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YearStatsTest {

    private BreakoutDay day(DayStatus status, RegimeStatus regime, Set<BreakoutCriterion> failed) {
        return new BreakoutDay("AAPL", LocalDate.of(2021, 3, 15), regime, status, failed);
    }

    private Set<BreakoutCriterion> none() {
        return EnumSet.noneOf(BreakoutCriterion.class);
    }

    @Test
    void shouldCountEachStatus() {
        YearStats stats = new YearStats();

        stats.record(day(DayStatus.OUTSIDE_UNIVERSE, RegimeStatus.RISK_ON, none()));
        stats.record(day(DayStatus.OUTSIDE_UNIVERSE, RegimeStatus.RISK_ON, none()));
        stats.record(day(DayStatus.NO_FLAG, RegimeStatus.RISK_ON, none()));
        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_ON,
                EnumSet.of(BreakoutCriterion.PULLBACK, BreakoutCriterion.ADR)));

        assertEquals(4, stats.judgedDays());
        assertEquals(2, stats.outsideUniverse());
        assertEquals(1, stats.noFlag());
        assertEquals(1, stats.evaluated());
        assertEquals(0, stats.signals());
    }

    @Test
    void shouldCountSignalsAndSplitByRegime() {
        YearStats stats = new YearStats();

        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_ON, none()));
        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_OFF, none()));
        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_OFF, none()));

        assertEquals(3, stats.signals());
        assertEquals(1, stats.signalsRiskOn());
    }

    @Test
    void shouldCountEveryFailedCriterion() {
        YearStats stats = new YearStats();

        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_ON,
                EnumSet.of(BreakoutCriterion.PULLBACK, BreakoutCriterion.PRIOR_MOVE)));
        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_ON,
                EnumSet.of(BreakoutCriterion.PULLBACK)));

        assertEquals(2, stats.failed(BreakoutCriterion.PULLBACK));
        assertEquals(1, stats.failed(BreakoutCriterion.PRIOR_MOVE));
        assertEquals(0, stats.failed(BreakoutCriterion.ADR));
    }

    @Test
    void nearMissShouldBeCountedSeparatelyAndNotDoubleCountFailures() {
        YearStats stats = new YearStats();

        // One near miss on PULLBACK, and one day where PULLBACK failed together with another
        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_ON,
                EnumSet.of(BreakoutCriterion.PULLBACK)));
        stats.record(day(DayStatus.EVALUATED, RegimeStatus.RISK_ON,
                EnumSet.of(BreakoutCriterion.PULLBACK, BreakoutCriterion.PRIOR_MOVE)));

        assertEquals(1, stats.nearMiss(BreakoutCriterion.PULLBACK));
        assertEquals(0, stats.nearMiss(BreakoutCriterion.PRIOR_MOVE));
        // Each day counts once per failed criterion, near miss or not
        assertEquals(2, stats.failed(BreakoutCriterion.PULLBACK));
    }

    @Test
    void emptyStatsShouldReturnZeros() {
        YearStats stats = new YearStats();

        assertEquals(0, stats.judgedDays());
        assertEquals(0, stats.failed(BreakoutCriterion.PULLBACK));
        assertEquals(0, stats.nearMiss(BreakoutCriterion.PULLBACK));
    }
}