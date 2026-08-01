package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.indicators.ConsolidationResult;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    public void calculateATR_withTooFewDays_returnsNull() {
        List<DailyPrice> prices = List.of(
                price("110", "100", "105")
        );

        assertNull(calculator.calculateATR(prices));
    }

    @Test
    public void calculateAverageVolume_withThreeValues_returnsAverage() {
        List<Long> volumes = List.of(1000L, 2000L, 3000L);

        Long result = calculator.calculateAverageVolume(volumes);

        // (1000 + 2000 + 3000) / 3 = 2000
        assertEquals(2000L, result);
    }

    @Test
    public void calculateAverageVolume_withEmptyList_returnsNull() {
        List<Long> volumes = List.of();

        Long result = calculator.calculateAverageVolume(volumes);

        assertNull(result);
    }

    @Test
    public void findPriorMove_withRisingPrices_returnsPercentageGain() {
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
    public void findPriorMove_withFallingPrices_returnsZero() {
        List<DailyPrice> prices = List.of(
                price("0", "0", "80"),   // highest but first
                price("0", "0", "65"),
                price("0", "0", "50")    // lowest but last
        );

        BigDecimal result = calculator.findPriorMove(prices);

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    public void findPriorMove_withEmptyList_returnsNull() {
        assertNull(calculator.findPriorMove(List.of()));
    }

    @Test
    public void shouldCalculateConsolidationRange() {
        // Högsta high = 110, lägsta low = 100
        // (110 - 100) / 100 * 100 = 10%
        List<DailyPrice> prices = List.of(
                priceWithHighLow("105", "102"),
                priceWithHighLow("110", "104"),
                priceWithHighLow("108", "100"));

        ConsolidationResult result = calculator.calculateConsolidation(prices);

        assertEquals(0, result.high().compareTo(new BigDecimal("110")));
        assertEquals(0, result.low().compareTo(new BigDecimal("100")));
        assertEquals(0, result.range().compareTo(new BigDecimal("10.0000")));
    }

    @Test
    public void shouldReturnNullForEmptyConsolidationList() {
        assertEquals(null, calculator.calculateConsolidation(List.of()));
    }

    /**
     * Helper: builds a DailyPrice with only high and low set,
     * since that's all calculateConsolidationRange looks at.
     */
    private DailyPrice priceWithHighLow(String high, String low) {
        DailyPrice p = new DailyPrice();
        p.setHigh(new BigDecimal(high));
        p.setLow(new BigDecimal(low));
        return p;
    }

    @Test
    public void shouldCalculateGapUp() {
        BigDecimal gap = calculator.calculateGap(new BigDecimal("100"), new BigDecimal("112"));

        assertEquals(0, gap.compareTo(new BigDecimal("12.0000")));
    }

    @Test
    public void shouldReturnNullGapWhenYesterdayCloseIsZero() {
        BigDecimal gap = calculator.calculateGap(BigDecimal.ZERO, new BigDecimal("112"));

        assertEquals(null, gap);
    }

    @Test
    public void shouldCalculateRelativeVolume() {
        BigDecimal relVol = calculator.calculateRelativeVolume(5_000_000L, 1_000_000L);

        assertEquals(0, relVol.compareTo(new BigDecimal("5.0000")));
    }

    @Test
    public void shouldReturnNullRelativeVolumeWhenAverageIsZero() {
        BigDecimal relVol = calculator.calculateRelativeVolume(5_000_000L, 0L);

        assertEquals(null, relVol);
    }

    @Test
    void shouldCalculateVolumeContraction() {
        // Flaggan: snittvolym 4M. Flaggstången: snittvolym 10M.
        // Kvot = 4M / 10M = 0.4 (volymen har torkat ut till 40%)
        List<DailyPrice> flag = List.of(
                priceWithVolume(4_000_000L),
                priceWithVolume(4_000_000L));
        List<DailyPrice> flagpole = List.of(
                priceWithVolume(10_000_000L),
                priceWithVolume(10_000_000L));

        BigDecimal contraction = calculator.calculateVolumeContraction(flag, flagpole);

        assertEquals(0, contraction.compareTo(new BigDecimal("0.4000")));
    }

    @Test
    void shouldReturnNullVolumeContractionWhenFlagpoleEmpty() {
        List<DailyPrice> flag = List.of(priceWithVolume(4_000_000L));
        List<DailyPrice> flagpole = List.of();

        assertNull(calculator.calculateVolumeContraction(flag, flagpole));
    }

    /**
     * Helper: builds a DailyPrice with only volume set,
     * since that's all calculateVolumeContraction looks at.
     */
    private DailyPrice priceWithVolume(Long volume) {
        DailyPrice p = new DailyPrice();
        p.setVolume(volume);
        return p;
    }

    @Test
    void calculateEMA_returnsNull_whenFewerPricesThanPeriod() {
        List<BigDecimal> closes = List.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(11), BigDecimal.valueOf(12));

        assertNull(calculator.calculateEMA(closes, 5));
    }

    @Test
    void calculateEMA_equalsSMA_whenExactlyPeriodPrices() {
        // Med exakt `period` priser hinner loopen aldrig köra - resultatet är
        // rena seed-värdet, alltså SMA:t.
        List<BigDecimal> closes = List.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(30));

        BigDecimal ema = calculator.calculateEMA(closes, 3);

        assertEquals(0, BigDecimal.valueOf(20).compareTo(ema)); // (10+20+30)/3
    }

    @Test
    void calculateEMA_weightsRecentPricesMoreHeavilyThanSMA() {
        // Stigande serie: EMA ska ligga NÄRMARE det senaste priset än vad SMA gör,
        // eftersom den viktar nyare data tyngre.
        List<BigDecimal> closes = List.of(
                BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(30),
                BigDecimal.valueOf(40), BigDecimal.valueOf(50));

        BigDecimal ema = calculator.calculateEMA(closes, 3);
        BigDecimal sma = calculator.calculateMA(closes);

        assertTrue(ema.compareTo(sma) > 0);
    }

    @Test
    void calculateEMA_returnsFlatValue_whenAllPricesIdentical() {
        List<BigDecimal> closes = List.of(
                BigDecimal.valueOf(25), BigDecimal.valueOf(25), BigDecimal.valueOf(25),
                BigDecimal.valueOf(25), BigDecimal.valueOf(25));

        BigDecimal ema = calculator.calculateEMA(closes, 3);

        assertEquals(0, BigDecimal.valueOf(25).compareTo(ema));
    }
}
