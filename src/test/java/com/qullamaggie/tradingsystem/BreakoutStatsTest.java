package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.BreakoutDay;
import com.qullamaggie.tradingsystem.backtest.BreakoutStats;
import com.qullamaggie.tradingsystem.backtest.DayStatus;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BreakoutStatsTest {

    private BreakoutDay day(LocalDate date, DayStatus status, Set<BreakoutCriterion> failed) {
        return new BreakoutDay("AAPL", date, RegimeStatus.RISK_ON, status, failed);
    }

    @Test
    void shouldSplitDaysByTheirOwnYear() {
        BreakoutStats stats = new BreakoutStats();

        stats.recordAll(List.of(
                day(LocalDate.of(2020, 3, 2), DayStatus.NO_FLAG, Set.of()),
                day(LocalDate.of(2020, 3, 3), DayStatus.NO_FLAG, Set.of()),
                day(LocalDate.of(2022, 6, 1), DayStatus.NO_FLAG, Set.of())));

        assertEquals(2, stats.byYear().get(2020).judgedDays());
        assertEquals(1, stats.byYear().get(2022).judgedDays());
        assertEquals(List.of(2020, 2022), List.copyOf(stats.byYear().keySet()));
    }

    @Test
    void shouldKeepOnlySignalsAndNearMisses() {
        BreakoutStats stats = new BreakoutStats();
        BreakoutDay signal = day(LocalDate.of(2021, 1, 4), DayStatus.EVALUATED,
                EnumSet.noneOf(BreakoutCriterion.class));
        BreakoutDay nearMiss = day(LocalDate.of(2021, 1, 5), DayStatus.EVALUATED,
                EnumSet.of(BreakoutCriterion.PULLBACK));
        BreakoutDay twoFailed = day(LocalDate.of(2021, 1, 6), DayStatus.EVALUATED,
                EnumSet.of(BreakoutCriterion.PULLBACK, BreakoutCriterion.ADR));
        BreakoutDay noFlag = day(LocalDate.of(2021, 1, 7), DayStatus.NO_FLAG, Set.of());

        stats.recordAll(List.of(signal, nearMiss, twoFailed, noFlag));

        assertEquals(List.of(signal, nearMiss), stats.interestingDays());
        // All four still count in the yearly numbers
        assertEquals(4, stats.byYear().get(2021).judgedDays());
    }
}