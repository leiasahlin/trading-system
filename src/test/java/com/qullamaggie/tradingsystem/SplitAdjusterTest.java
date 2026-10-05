package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.dto.StockSplit;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.service.SplitAdjuster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SplitAdjusterTest {

    private SplitAdjuster adjuster;

    @BeforeEach
    void setUp() {
        adjuster = new SplitAdjuster();
    }

    private DailyPrice price(LocalDate date, String close, long volume) {
        DailyPrice p = new DailyPrice();
        p.setDate(date);
        p.setOpen(new BigDecimal(close));
        p.setHigh(new BigDecimal(close));
        p.setLow(new BigDecimal(close));
        p.setClose(new BigDecimal(close));
        p.setVolume(volume);
        return p;
    }

    // --- looksLikeSplit ---

    @Test
    void looksLikeSplit_detectsReverseSplitJump() {
        // 0.27 -> 12.77, som NFE:s 1:50
        List<DailyPrice> prices = List.of(
                price(LocalDate.of(2026, 9, 9), "0.273", 5_400_400),
                price(LocalDate.of(2026, 9, 15), "12.77", 510_600));

        assertTrue(adjuster.looksLikeSplit(prices));
    }

    @Test
    void looksLikeSplit_detectsForwardSplitDrop() {
        // 400 -> 100, en 4-for-1 split
        List<DailyPrice> prices = List.of(
                price(LocalDate.of(2026, 9, 9), "400", 100_000),
                price(LocalDate.of(2026, 9, 10), "100", 400_000));

        assertTrue(adjuster.looksLikeSplit(prices));
    }

    @Test
    void looksLikeSplit_ignoresNormalMoves() {
        // +30 % på en dag är våldsamt men inte en split
        List<DailyPrice> prices = List.of(
                price(LocalDate.of(2026, 9, 9), "100", 100_000),
                price(LocalDate.of(2026, 9, 10), "130", 300_000),
                price(LocalDate.of(2026, 9, 11), "125", 200_000));

        assertFalse(adjuster.looksLikeSplit(prices));
    }

    @Test
    void looksLikeSplit_handlesEmptyAndSingleEntryLists() {
        assertFalse(adjuster.looksLikeSplit(List.of()));
        assertFalse(adjuster.looksLikeSplit(List.of(price(LocalDate.of(2026, 9, 9), "100", 1000))));
    }

    // --- adjust ---

    @Test
    void adjust_scalesPricesBeforeSplitAndLeavesLaterOnesAlone() {
        LocalDate splitDate = LocalDate.of(2026, 9, 14);
        DailyPrice before = price(LocalDate.of(2026, 9, 9), "0.273", 5_000_000);
        DailyPrice after = price(LocalDate.of(2026, 9, 15), "12.77", 500_000);

        adjuster.adjust(List.of(before, after),
                List.of(new StockSplit(splitDate, BigDecimal.valueOf(50))));

        // 0.273 * 50 = 13.65, volymen delas med 50
        assertEquals(0, new BigDecimal("13.65").compareTo(before.getClose()));
        assertEquals(100_000, before.getVolume());

        // Efter splitdatumet rörs ingenting
        assertEquals(0, new BigDecimal("12.77").compareTo(after.getClose()));
        assertEquals(500_000, after.getVolume());
    }

    @Test
    void adjust_scalesDownOnForwardSplit() {
        // 4-for-1: ratio 0.25, priser före delas alltså på fyra
        DailyPrice before = price(LocalDate.of(2026, 9, 9), "400", 100_000);

        adjuster.adjust(List.of(before),
                List.of(new StockSplit(LocalDate.of(2026, 9, 14), new BigDecimal("0.25"))));

        assertEquals(0, new BigDecimal("100").compareTo(before.getClose()));
        assertEquals(400_000, before.getVolume());
    }

    @Test
    void adjust_appliesEverySplitWhenSeveralExist() {
        // Två splittar: en äldre rad passerar båda och skalas 2 x 10 = 20 gånger
        DailyPrice oldest = price(LocalDate.of(2026, 1, 5), "1", 1_000_000);
        DailyPrice middle = price(LocalDate.of(2026, 6, 5), "10", 100_000);

        adjuster.adjust(List.of(oldest, middle), List.of(
                new StockSplit(LocalDate.of(2026, 3, 1), BigDecimal.valueOf(2)),
                new StockSplit(LocalDate.of(2026, 9, 1), BigDecimal.valueOf(10))));

        assertEquals(0, new BigDecimal("20").compareTo(oldest.getClose()));
        assertEquals(0, new BigDecimal("100").compareTo(middle.getClose()));
    }

    @Test
    void adjust_doesNothingWithoutSplits() {
        DailyPrice unchanged = price(LocalDate.of(2026, 9, 9), "100", 50_000);

        adjuster.adjust(List.of(unchanged), List.of());

        assertEquals(0, new BigDecimal("100").compareTo(unchanged.getClose()));
    }

    @Test
    void looksLikeSplit_ignoresRowsWithMissingClose() {
        // Ofullständig data från leverantören ska hoppas över, inte krascha
        DailyPrice incomplete = new DailyPrice();
        incomplete.setDate(LocalDate.of(2026, 9, 10));

        List<DailyPrice> prices = List.of(
                price(LocalDate.of(2026, 9, 9), "100", 100_000),
                incomplete,
                price(LocalDate.of(2026, 9, 11), "102", 110_000));

        assertFalse(adjuster.looksLikeSplit(prices));
    }

    @Test
    void looksLikeSplit_detectsSplitEvenWhenAnotherRowIsIncomplete() {
        // En saknad rad ska inte dölja en split senare i serien
        DailyPrice incomplete = new DailyPrice();
        incomplete.setDate(LocalDate.of(2026, 9, 10));

        List<DailyPrice> prices = List.of(
                price(LocalDate.of(2026, 9, 9), "100", 100_000),
                incomplete,
                price(LocalDate.of(2026, 9, 11), "0.5", 110_000),
                price(LocalDate.of(2026, 9, 12), "25", 110_000));

        assertTrue(adjuster.looksLikeSplit(prices));
    }
}