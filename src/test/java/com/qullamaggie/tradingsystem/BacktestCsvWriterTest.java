package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.BacktestCsvWriter;
import com.qullamaggie.tradingsystem.backtest.BreakoutDay;
import com.qullamaggie.tradingsystem.backtest.BreakoutStats;
import com.qullamaggie.tradingsystem.backtest.DayStatus;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BacktestCsvWriterTest {

    // JUnit creates an empty folder for each test and deletes it afterwards
    @TempDir
    Path tempDir;

    private BacktestCsvWriter writer;
    private BreakoutStats stats;

    @BeforeEach
    void setUp() {
        writer = new BacktestCsvWriter();
        stats = new BreakoutStats();
        stats.recordAll(List.of(
                new BreakoutDay("NVDA", LocalDate.of(2020, 5, 14), RegimeStatus.RISK_ON,
                        DayStatus.EVALUATED, EnumSet.noneOf(BreakoutCriterion.class)),
                new BreakoutDay("TSLA", LocalDate.of(2020, 6, 2), RegimeStatus.RISK_OFF,
                        DayStatus.EVALUATED, EnumSet.of(BreakoutCriterion.PULLBACK)),
                new BreakoutDay("AMD", LocalDate.of(2020, 6, 3), RegimeStatus.RISK_ON,
                        DayStatus.NO_FLAG, Set.of()),
                new BreakoutDay("AMD", LocalDate.of(2021, 2, 1), RegimeStatus.RISK_ON,
                        DayStatus.OUTSIDE_UNIVERSE, Set.of())));
    }

    @Test
    void summaryShouldHaveHeaderAndOneRowPerYear() throws Exception {
        Path file = writer.writeSummary(stats, tempDir.resolve("summary.csv"));

        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());               // header + 2020 + 2021
        assertTrue(lines.get(0).startsWith("year;judged_days;outside_universe;no_flag;"));
        assertTrue(lines.get(1).startsWith("2020;"));
        assertTrue(lines.get(2).startsWith("2021;"));
    }

    @Test
    void summaryRowShouldMatchHeaderColumns() throws Exception {
        Path file = writer.writeSummary(stats, tempDir.resolve("summary.csv"));

        List<String> lines = Files.readAllLines(file);
        String[] header = lines.get(0).split(";", -1);
        String[] row2020 = lines.get(1).split(";", -1);

        // 7 fixed columns + 2 per criterion
        assertEquals(7 + 2 * BreakoutCriterion.values().length, header.length);
        assertEquals(header.length, row2020.length);

        // Look values up by column name, so the test doesn't depend on column order
        assertEquals("3", valueOf("judged_days", header, row2020));
        assertEquals("1", valueOf("no_flag", header, row2020));
        assertEquals("2", valueOf("evaluated", header, row2020));
        assertEquals("1", valueOf("signals", header, row2020));
        assertEquals("1", valueOf("signals_risk_on", header, row2020));
        assertEquals("1", valueOf("failed_PULLBACK", header, row2020));
        assertEquals("1", valueOf("near_miss_PULLBACK", header, row2020));
        assertEquals("0", valueOf("failed_ADR", header, row2020));
    }

    @Test
    void signalsFileShouldListSignalsAndNearMisses() throws Exception {
        Path file = writer.writeSignals(stats, tempDir.resolve("signals.csv"));

        List<String> lines = Files.readAllLines(file);
        assertEquals(List.of(
                "symbol;date;regime;type;failed_criterion",
                "NVDA;2020-05-14;RISK_ON;SIGNAL;",
                "TSLA;2020-06-02;RISK_OFF;NEAR_MISS;PULLBACK"), lines);
    }

    @Test
    void shouldCreateMissingFolder() throws Exception {
        Path file = tempDir.resolve("backtest-results").resolve("signals.csv");

        writer.writeSignals(stats, file);

        assertTrue(Files.exists(file));
    }

    private String valueOf(String column, String[] header, String[] row) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(column)) {
                return row[i];
            }
        }
        fail("Column " + column + " missing");
        return null;
    }
}