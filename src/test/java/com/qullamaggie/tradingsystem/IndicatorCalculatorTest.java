package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IndicatorCalculatorTest {
    private final IndicatorCalculator calculator = new IndicatorCalculator();

    @Test
    public void calculateMA_withFiveValues_returnsCorrectAverage() {
        List<BigDecimal> closes = List.of(
                new BigDecimal("100"),
                new BigDecimal("102"),
                new BigDecimal("101"),
                new BigDecimal("105"),
                new BigDecimal("107")
        );

        BigDecimal result = calculator.calculateMA(closes);

        //103+102+101+105+10+7 / 5
        assertEquals(new BigDecimal("103.0000"), result);
    }

    @Test
    public void calculateMA_withEmptyList_returnsNull() {
        IndicatorCalculator calculator = new IndicatorCalculator();

        BigDecimal result = calculator.calculateMA(List.of());

        assertNull(result);
    }

    private DailyPrice price (String high, String low, String close) {
        DailyPrice dp = new DailyPrice();
        dp.setHigh(new BigDecimal(high));
        dp.setLow(new BigDecimal(low));
        dp.setClose(new BigDecimal(close));
        dp.setOpen(new BigDecimal("100")); // dummy, not used in ADR test
        dp.setVolume(1000L); // dummy, not used in ADR test
        dp.setDate(LocalDate.now()); // dummy
        return dp;

    }

    @Test
    public void calculateADR_withThreeDays_returnsCorrectPercentage() {
        List<DailyPrice> prices = List.of(
                //Day 1: Only close is used (yesterday close)
                price("0", "0", "100"),
                //Day 2: range = (110-100)/100 = 0,10
                price("110", "100", "100"),
                //Day 3: range = (105-102)/103 = 0,03
                price("105", "102", "104")
        );

        BigDecimal result = calculator.calculateADR(prices);

        assertEquals(new BigDecimal("6.5000"), result);
    }

    @Test
    public void calculateADR_withTooFewDays_returnNull() {
        List<DailyPrice> prices = List.of(
                price("110", "100", "105")
        );

        assertNull(calculator.calculateADR(prices));
    }

    @Test
    public void calculateATR_withGapUp_usesGapInTrueRange() {
        List<DailyPrice> prices = List.of(
                price("0", "0", "100"),      // Day 0: Only close is used
                price("103", "98", "102"),   // Day 1: no gap
                price("112", "110", "111")   // Day 2: Gap
        );

        BigDecimal result = calculator.calculateATR(prices);

        assertEquals(new BigDecimal("7.5000"), result);
    }

    @Test
    void calculateATR_withTooFewDays_returnsNull() {
        List<DailyPrice> prices = List.of(
                price("110", "100", "105")
        );

        assertNull(calculator.calculateATR(prices));
    }

    @Test
    void calculateAverageVolume_withThreeValues_returnsAverage() {
        List<Long> volumes = List.of(1000L, 2000L, 3000L);

        Long result = calculator.calculateAverageVolume(volumes);

        // (1000 + 2000 + 3000) / 3 = 2000
        assertEquals(2000L, result);
    }

    @Test
    void calculateAverageVolume_withEmptyList_returnsNull() {
        List<Long> volumes = List.of();

        Long result = calculator.calculateAverageVolume(volumes);

        assertNull(result);
    }

    @Test
    void findPriorMove_withRisingPrices_returnsPercentageGain() {
        List<DailyPrice> prices = List.of(
                price("0", "0", "50"),   // low point
                price("0", "0", "60"),
                price("0", "0", "80")    // highest after low point
        );

        BigDecimal result = calculator.findPriorMove(prices);

        // (80 - 50) / 50 = 0,60 → 60%
        assertEquals(new BigDecimal("60.0000"), result);
    }

    @Test
    void findPriorMove_withFallingPrices_returnsZero() {
        List<DailyPrice> prices = List.of(
                price("0", "0", "80"),   // highest but first
                price("0", "0", "65"),
                price("0", "0", "50")    // lowest but last
        );

        BigDecimal result = calculator.findPriorMove(prices);

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    void findPriorMove_withEmptyList_returnsNull() {
        assertNull(calculator.findPriorMove(List.of()));
    }
}
