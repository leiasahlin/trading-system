package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.indicators.FlagPhases;
import com.qullamaggie.tradingsystem.indicators.PhaseDetectionConfig;
import com.qullamaggie.tradingsystem.indicators.PhaseDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhaseDetectorTest {

    private PhaseDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PhaseDetector(new PhaseDetectionConfig(10, 40, 60));
    }

    /**
     * Bygger en prislista där alla dagar har samma high, utom den dag som ska
     * utgöra toppen. Äldst först, precis som detektorn förväntar sig.
     */
    private List<DailyPrice> pricesWithPeakAt(int size, int peakIndex) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            DailyPrice p = new DailyPrice();
            p.setDate(LocalDate.of(2026, 1, 1).plusDays(i));
            BigDecimal high = i == peakIndex ? new BigDecimal("200") : new BigDecimal("100");
            p.setHigh(high);
            p.setLow(new BigDecimal("90"));
            p.setOpen(new BigDecimal("95"));
            p.setClose(new BigDecimal("95"));
            p.setVolume(1_000_000L);
            prices.add(p);
        }
        return prices;
    }

    @Test
    void detect_returnsNull_withTooLittleHistory() {
        // Kräver minst maxFlagDays + flagpoleDays = 100 dagar
        assertNull(detector.detect(pricesWithPeakAt(99, 80)));
    }

    @Test
    void detect_splitsAtThePeak_whenFlagLengthIsValid() {
        // Topp vid index 100 av 120 -> flaggan är de 20 dagar som följer
        List<DailyPrice> prices = pricesWithPeakAt(120, 100);

        FlagPhases phases = detector.detect(prices);

        assertNotNull(phases);
        assertEquals(20, phases.flag().size());
        assertEquals(60, phases.flagpole().size());
        // Flaggan börjar på toppdagen
        assertEquals(0, new BigDecimal("200").compareTo(phases.flag().getFirst().getHigh()));
    }

    @Test
    void detect_returnsNull_whenPeakIsTooRecent() {
        // Topp 5 dagar bak - ingen konsolidering har hunnit bildas
        assertNull(detector.detect(pricesWithPeakAt(120, 115)));
    }

    @Test
    void detect_returnsNull_whenPeakSitsAtTheWindowEdge() {
        // Topp på sökfönstrets första dag: den verkliga toppen kan ligga ännu
        // längre bak, alltså är flaggan för lång för att vara giltig
    }
}