package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.dto.IntradayBar;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.scanner.ParabolicIntradayEvaluator;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParabolicIntradayEvaluatorTest {

    private ParabolicIntradayEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ParabolicShortConfig config = new ParabolicShortConfig(
                BigDecimal.valueOf(5), BigDecimal.valueOf(50), BigDecimal.valueOf(120),
                BigDecimal.valueOf(300), BigDecimal.valueOf(2_000_000_000L),
                BigDecimal.valueOf(10_000_000_000L), 15, 3, BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(2.0), BigDecimal.valueOf(2), BigDecimal.valueOf(0.5), 15);
        evaluator = new ParabolicIntradayEvaluator(new IndicatorCalculator(), config);
    }

    private IntradayBar bar(int hour, int minute, String high, String low, String close) {
        return new IntradayBar(LocalDateTime.of(2026, 9, 18, hour, minute),
                new BigDecimal(close), new BigDecimal(high), new BigDecimal(low),
                new BigDecimal(close), 100);
    }

    // --- opening range ---

    @Test
    void openingRangeLow_isLowestLowWithinFirstFifteenMinutes() {
        List<IntradayBar> bars = List.of(
                bar(9, 30, "101", "100", "100.5"),
                bar(9, 35, "100", "98", "99"),      // lägst
                bar(9, 40, "100", "99", "99.5"),
                bar(9, 45, "99", "95", "96"));      // utanför intervallet, räknas inte

        assertEquals(0, new BigDecimal("98").compareTo(evaluator.openingRangeLow(bars)));
    }

    @Test
    void hasBrokenBelowOpeningRangeLow_returnsTrue_whenLaterBarClosesBelow() {
        List<IntradayBar> bars = List.of(
                bar(9, 30, "101", "100", "100.5"),
                bar(9, 35, "100", "98", "99"),
                bar(9, 40, "100", "99", "99.5"),
                bar(9, 45, "99", "96", "97"));      // close 97 < ORL 98

        assertTrue(evaluator.hasBrokenBelowOpeningRangeLow(bars));
    }

    @Test
    void hasBrokenBelowOpeningRangeLow_returnsFalse_whileStillInsideOpeningRange() {
        // 09:40 stänger under tidigare lägsta, men intervallet är inte slut än
        List<IntradayBar> bars = List.of(
                bar(9, 30, "101", "100", "100.5"),
                bar(9, 35, "100", "98", "99"),
                bar(9, 40, "99", "96", "97"));

        assertFalse(evaluator.hasBrokenBelowOpeningRangeLow(bars));
    }

    @Test
    void hasBrokenBelowOpeningRangeLow_returnsFalse_whenHoldingAboveLow() {
        List<IntradayBar> bars = List.of(
                bar(9, 30, "101", "100", "100.5"),
                bar(9, 35, "100", "98", "99"),
                bar(9, 40, "100", "99", "99.5"),
                bar(9, 45, "100", "98.5", "99"));

        assertFalse(evaluator.hasBrokenBelowOpeningRangeLow(bars));
    }

    // --- VWAP ---

    @Test
    void hasFailedVwapReclaim_returnsTrue_onBelowThenTestThenBelow() {
        // VWAP ≈ 99.3: föregående close 99 under, sista high 100.2 når upp, sista close 98.5 under
        List<IntradayBar> bars = List.of(
                bar(9, 30, "100", "100", "100"),
                bar(9, 35, "99", "99", "99"),
                bar(9, 40, "100.2", "98", "98.5"));

        assertTrue(evaluator.hasFailedVwapReclaim(bars));
    }

    @Test
    void hasFailedVwapReclaim_returnsFalse_whenReclaimSucceeds() {
        // Sista stapeln stänger ÖVER VWAP - återerövringen lyckades
        List<IntradayBar> bars = List.of(
                bar(9, 30, "100", "100", "100"),
                bar(9, 35, "99", "99", "99"),
                bar(9, 40, "100.2", "98", "99.5"));

        assertFalse(evaluator.hasFailedVwapReclaim(bars));
    }

    @Test
    void hasFailedVwapReclaim_returnsFalse_whenPreviousBarWasAboveVwap() {
        // Ingen förlorad nivå att återerövra
        List<IntradayBar> bars = List.of(
                bar(9, 30, "100", "100", "100"),
                bar(9, 35, "101", "101", "101"),
                bar(9, 40, "100.5", "98", "98.5"));

        assertFalse(evaluator.hasFailedVwapReclaim(bars));
    }

    @Test
    void hasFailedVwapReclaim_returnsFalse_withFewerThanTwoBars() {
        assertFalse(evaluator.hasFailedVwapReclaim(List.of(bar(9, 30, "100", "98", "99"))));
    }
}
