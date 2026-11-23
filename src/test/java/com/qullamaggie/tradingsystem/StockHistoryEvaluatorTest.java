package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.BreakoutDay;
import com.qullamaggie.tradingsystem.backtest.DayStatus;
import com.qullamaggie.tradingsystem.backtest.StockHistoryEvaluator;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.indicators.PhaseDetectionConfig;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluation;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluator;
import com.qullamaggie.tradingsystem.universe.UniverseFilterEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StockHistoryEvaluatorTest {

    private static final LocalDate START = LocalDate.of(2020, 1, 1);

    @Mock private IndicatorService indicatorService;
    @Mock private UniverseFilterEvaluator universeFilter;
    @Mock private BreakoutEvaluator breakoutEvaluator;

    private StockHistoryEvaluator evaluator;
    private List<DailyPrice> prices;

    @BeforeEach
    void setUp() {
        // maxFlag 40 + flagpole 60 = 100 days of history before the first judged day
        evaluator = new StockHistoryEvaluator(indicatorService, universeFilter, breakoutEvaluator,
                new PhaseDetectionConfig(10, 40, 60));
        prices = buildPrices(120);
    }

    @Test
    void shouldStartAtFirstDayWithEnoughHistory() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, Map.of(), START.minusYears(1));

        // Position 99 is the 100th day, the first one with enough history
        assertEquals(120 - 99, days.size());
        assertEquals(prices.get(99).getDate(), days.getFirst().date());
    }

    @Test
    void shouldSkipDaysBeforeFrom() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());
        LocalDate from = prices.get(110).getDate();

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, Map.of(), from);

        assertEquals(10, days.size());
        assertEquals(from, days.getFirst().date());
        // Indicators are not calculated for skipped days
        verify(indicatorService, times(10)).calculateIndicators(anyList());
    }

    @Test
    void indicatorsShouldOnlySeePricesUpToTheJudgedDay() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, Map.of(), START.minusYears(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(indicatorService, times(days.size())).calculateIndicators(captor.capture());

        List<List<DailyPrice>> calls = captor.getAllValues();
        for (int k = 0; k < days.size(); k++) {
            List<DailyPrice> seen = calls.get(k);
            // The last price the indicators saw is the day being judged, never a later one
            assertEquals(days.get(k).date(), seen.getLast().getDate());
            assertEquals(99 + k + 1, seen.size());
        }
    }

    @Test
    void dayOutsideUniverseShouldNotBeEvaluatedForBreakout() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());
        when(universeFilter.isEligible(any(), any())).thenReturn(false);

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, Map.of(), START.minusYears(1));

        assertTrue(days.stream().allMatch(d -> d.status() == DayStatus.OUTSIDE_UNIVERSE));
        verify(breakoutEvaluator, never()).evaluate(any(), any());
    }

    @Test
    void dayWithoutFlagShouldBeNoFlag() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());
        when(universeFilter.isEligible(any(), any())).thenReturn(true);
        when(breakoutEvaluator.evaluate(any(), any())).thenReturn(Optional.empty());

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, Map.of(), START.minusYears(1));

        assertTrue(days.stream().allMatch(d -> d.status() == DayStatus.NO_FLAG));
        assertTrue(days.stream().allMatch(d -> d.failed().isEmpty()));
    }

    @Test
    void evaluatedDayShouldCarryFailedCriteria() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());
        when(universeFilter.isEligible(any(), any())).thenReturn(true);
        when(breakoutEvaluator.evaluate(any(), any())).thenReturn(Optional.of(
                new BreakoutEvaluation(EnumSet.of(BreakoutCriterion.PULLBACK))));

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, Map.of(), START.minusYears(1));

        BreakoutDay first = days.getFirst();
        assertEquals(DayStatus.EVALUATED, first.status());
        assertEquals(EnumSet.of(BreakoutCriterion.PULLBACK), first.failed());
        assertTrue(first.isNearMiss());
    }

    @Test
    void breakoutShouldBeJudgedOnTheDaysOwnPrice() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());
        when(universeFilter.isEligible(any(), any())).thenReturn(true);
        when(breakoutEvaluator.evaluate(any(), any())).thenReturn(Optional.empty());
        LocalDate from = prices.getLast().getDate();

        evaluator.evaluate("AAPL", prices, Map.of(), from);

        verify(universeFilter).isEligible(eqDate(from), any());
        verify(breakoutEvaluator).evaluate(eqDate(from), any());
    }

    @Test
    void shouldUseRegimeForTheDayAndDefaultToRiskOff() {
        when(indicatorService.calculateIndicators(anyList())).thenAnswer(inv -> new Indicator());
        LocalDate riskOnDay = prices.get(100).getDate();
        Map<LocalDate, RegimeStatus> regimes = Map.of(riskOnDay, RegimeStatus.RISK_ON);

        List<BreakoutDay> days = evaluator.evaluate("AAPL", prices, regimes, START.minusYears(1));

        assertEquals(RegimeStatus.RISK_OFF, days.get(0).regime());   // position 99, missing in map
        assertEquals(RegimeStatus.RISK_ON, days.get(1).regime());    // position 100
    }

    @Test
    void tooShortHistoryShouldGiveNoDays() {
        List<BreakoutDay> days = evaluator.evaluate("AAPL", buildPrices(99), Map.of(), START.minusYears(1));

        assertTrue(days.isEmpty());
        verifyNoInteractions(indicatorService);
    }

    /** Matches the DailyPrice for a given date. */
    private DailyPrice eqDate(LocalDate date) {
        return argThat(p -> p != null && p.getDate().equals(date));
    }

    /** Consecutive days, oldest first. */
    private List<DailyPrice> buildPrices(int count) {
        List<DailyPrice> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyPrice p = new DailyPrice();
            p.setDate(START.plusDays(i));
            p.setClose(BigDecimal.TEN);
            list.add(p);
        }
        return list;
    }
}