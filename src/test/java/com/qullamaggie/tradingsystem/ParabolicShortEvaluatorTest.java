package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortConfig;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParabolicShortEvaluatorTest {

    private ParabolicShortEvaluator evaluator;

    private static final BigDecimal LARGE_CAP = BigDecimal.valueOf(50_000_000_000L);
    private static final BigDecimal MID_CAP = BigDecimal.valueOf(5_000_000_000L);
    private static final BigDecimal SMALL_CAP = BigDecimal.valueOf(1_000_000_000L);
    private static final Long AVG_VOLUME = 1_000_000L;

    @BeforeEach
    void setUp() {
        ParabolicShortConfig config = new ParabolicShortConfig(
                BigDecimal.valueOf(5),                  // minDailyGainPercent
                BigDecimal.valueOf(50),                 // minLargeCapMovePercent
                BigDecimal.valueOf(120),                // minMidCapMovePercent
                BigDecimal.valueOf(300),                // minSmallCapMovePercent
                BigDecimal.valueOf(2_000_000_000L),     // smallCapMaxUsd
                BigDecimal.valueOf(10_000_000_000L),    // largeCapMinUsd
                15,                                     // lookbackDays
                3,                                      // minConsecutiveUpDays
                BigDecimal.valueOf(1.5),                // minBreakdownVolumeRatio
                BigDecimal.valueOf(2.0),                // minChurnVolumeRatio
                BigDecimal.valueOf(2),                  // maxChurnPricePercent
                BigDecimal.valueOf(0.5));               // stopMarginPercent

        evaluator = new ParabolicShortEvaluator(config, new IndicatorCalculator());
    }

    /** Builds a price bar. High defaults to close, volume to the average. */
    private DailyPrice price(double close) {
        return price(close, close, AVG_VOLUME);
    }

    private DailyPrice price(double close, double high, Long volume) {
        DailyPrice p = new DailyPrice();
        p.setClose(BigDecimal.valueOf(close));
        p.setHigh(BigDecimal.valueOf(high));
        p.setLow(BigDecimal.valueOf(close));
        p.setOpen(BigDecimal.valueOf(close));
        p.setVolume(volume);
        p.setDate(LocalDate.of(2026, 1, 5));
        return p;
    }

    private List<DailyPrice> prices(double... closes) {
        List<DailyPrice> list = new ArrayList<>();
        for (double c : closes) {
            list.add(price(c));
        }
        return list;
    }

    // --- countConsecutiveUpDays ---

    @Test
    void countConsecutiveUpDays_countsTrailingRun() {
        List<DailyPrice> p = prices(100, 120, 110, 118, 130, 145);

        assertEquals(3, evaluator.countConsecutiveUpDays(p));
    }

    @Test
    void countConsecutiveUpDays_returnsZero_whenLatestDayIsDown() {
        List<DailyPrice> p = prices(100, 110, 120, 115);

        assertEquals(0, evaluator.countConsecutiveUpDays(p));
    }

    @Test
    void countConsecutiveUpDays_returnsZero_whenFlatDay() {
        // Oförändrad stängning är inte en uppgångsdag
        List<DailyPrice> p = prices(100, 110, 110);

        assertEquals(0, evaluator.countConsecutiveUpDays(p));
    }

    // --- isAccelerating ---

    @Test
    void isAccelerating_returnsTrue_whenEachGainExceedsPrevious() {
        // 6.0%, 7.5%, 9.6% - stigande takt, alla över 5%-tröskeln
        List<DailyPrice> p = prices(100, 106, 114, 125);

        assertTrue(evaluator.isAccelerating(p));
    }

    @Test
    void isAccelerating_returnsFalse_whenRateDecelerates() {
        // 10.0%, 5.45%, 12.07% - dag två bromsar in
        List<DailyPrice> p = prices(100, 110, 116, 130);

        assertFalse(evaluator.isAccelerating(p));
    }

    @Test
    void isAccelerating_returnsFalse_whenGainsBelowMinimum() {
        // 3.0%, 4.0%, 5.0% - strikt stigande MEN de två första är under 5%
        List<DailyPrice> p = prices(100, 103, 107.12, 112.48);

        assertFalse(evaluator.isAccelerating(p));
    }

    @Test
    void isAccelerating_returnsFalse_whenTooFewPrices() {
        // Två priser = bara en dagsförändring, inget att jämföra mot
        List<DailyPrice> p = prices(100, 110);

        assertFalse(evaluator.isAccelerating(p));
    }

    // --- isExtended ---

    @Test
    void isExtended_returnsTrue_forLargeCap_whenMoveExceedsFiftyPercent() {
        // 100 -> 160 = 60% rörelse
        List<DailyPrice> p = prices(100, 106, 114, 125, 160);

        assertTrue(evaluator.isExtended(p, LARGE_CAP));
    }

    @Test
    void isExtended_returnsFalse_forMidCap_whenSameMoveIsBelowItsThreshold() {
        // Samma 60% räcker inte för mid cap (kräver 120%)
        List<DailyPrice> p = prices(100, 106, 114, 125, 160);

        assertFalse(evaluator.isExtended(p, MID_CAP));
    }

    @Test
    void isExtended_returnsFalse_forSmallCap_whenSameMoveIsBelowItsThreshold() {
        // Samma 60% räcker inte alls för small cap (kräver 300%)
        List<DailyPrice> p = prices(100, 106, 114, 125, 160);

        assertFalse(evaluator.isExtended(p, SMALL_CAP));
    }

    @Test
    void isExtended_returnsFalse_whenMarketCapIsNull() {
        List<DailyPrice> p = prices(100, 160);

        assertFalse(evaluator.isExtended(p, null));
    }

    // --- hasBrokenBelowEma ---

    @Test
    void hasBrokenBelowEma_returnsTrue_whenCloseBelowEmaOnHighVolume() {
        assertTrue(evaluator.hasBrokenBelowEma(
                BigDecimal.valueOf(95), BigDecimal.valueOf(100), 2_000_000L, AVG_VOLUME));
    }

    @Test
    void hasBrokenBelowEma_returnsFalse_whenCloseAboveEma() {
        assertFalse(evaluator.hasBrokenBelowEma(
                BigDecimal.valueOf(105), BigDecimal.valueOf(100), 2_000_000L, AVG_VOLUME));
    }

    @Test
    void hasBrokenBelowEma_returnsFalse_whenVolumeTooLow() {
        // Brott under EMA men bara 1.2x snittvolym - under 1.5-tröskeln
        assertFalse(evaluator.hasBrokenBelowEma(
                BigDecimal.valueOf(95), BigDecimal.valueOf(100), 1_200_000L, AVG_VOLUME));
    }

    // --- isChurning ---

    @Test
    void isChurning_returnsTrue_atNewHighOnExtremeVolumeWithStalledPrice() {
        List<DailyPrice> p = new ArrayList<>(prices(100, 110, 120));
        // Dagens high är högst i listan, 3x volym, men bara +1% i pris
        p.add(price(121.2, 130, 3_000_000L));

        assertTrue(evaluator.isChurning(p, AVG_VOLUME));
    }

    @Test
    void isChurning_returnsFalse_whenPriceMovedTooMuch() {
        List<DailyPrice> p = new ArrayList<>(prices(100, 110, 120));
        // Ny topp och extremvolym, men +8% - priset står inte still
        p.add(price(129.6, 135, 3_000_000L));

        assertFalse(evaluator.isChurning(p, AVG_VOLUME));
    }

    @Test
    void isChurning_returnsFalse_whenNotAtNewHigh() {
        List<DailyPrice> p = new ArrayList<>(prices(100, 110, 120));
        // Extremvolym och stillastående pris, men high (115) understiger tidigare 120
        p.add(price(114, 115, 3_000_000L));

        assertFalse(evaluator.isChurning(p, AVG_VOLUME));
    }

    @Test
    void isChurning_returnsFalse_whenVolumeNotExtreme() {
        List<DailyPrice> p = new ArrayList<>(prices(100, 110, 120));
        // Ny topp och stillastående pris, men bara 1.5x volym (kräver 2.0x)
        p.add(price(121.2, 130, 1_500_000L));

        assertFalse(evaluator.isChurning(p, AVG_VOLUME));
    }

    // --- isParabolicShort (helheten) ---

    @Test
    void isParabolicShort_returnsTrue_whenAllCriteriaAndEmaBreakdownAlign() {
        // 100 -> 160 = 60% (large cap OK), tre accelererande uppgångsdagar
        // (6.0%, 7.5%, 9.6%), sedan brott under EMA på hög volym
        List<DailyPrice> p = new ArrayList<>(prices(100, 106, 114, 125));
        p.add(price(160, 165, 2_500_000L));

        // OBS: sista dagen är också en uppgångsdag, så serien blir fyra dagar
        // och EMA-brottet kollas mot dess close (160) - sätt EMA över den.
        assertTrue(evaluator.isParabolicShort(p, LARGE_CAP, BigDecimal.valueOf(170), AVG_VOLUME));
    }

    @Test
    void isParabolicShort_returnsFalse_whenExtensionInsufficient() {
        // Samma struktur men mid cap-tröskeln (120%) nås inte av 60%
        List<DailyPrice> p = new ArrayList<>(prices(100, 106, 114, 125));
        p.add(price(160, 165, 2_500_000L));

        assertFalse(evaluator.isParabolicShort(p, MID_CAP, BigDecimal.valueOf(170), AVG_VOLUME));
    }

    @Test
    void isParabolicShort_returnsFalse_whenNoBreakdownTrigger() {
        // Extension och struktur OK, men close ligger ÖVER EMA och ingen churning
        List<DailyPrice> p = new ArrayList<>(prices(100, 106, 114, 125));
        p.add(price(160, 165, 2_500_000L));

        assertFalse(evaluator.isParabolicShort(p, LARGE_CAP, BigDecimal.valueOf(140), AVG_VOLUME));
    }

    @Test
    void isParabolicShort_returnsFalse_whenTooFewConsecutiveUpDays() {
        // Bara två uppgångsdagar i slutet (125 -> 110 bryter serien)
        List<DailyPrice> p = new ArrayList<>(prices(100, 106, 114, 125, 110, 130));
        p.add(price(160, 165, 2_500_000L));

        assertFalse(evaluator.isParabolicShort(p, LARGE_CAP, BigDecimal.valueOf(170), AVG_VOLUME));
    }
}
