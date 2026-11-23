package com.qullamaggie.tradingsystem.backtest;

import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes backtest results as semicolon-separated files, which
 * Swedish Excel opens straight into columns.
 */
@Component
public class BacktestCsvWriter {

    private static final String SEP = ";";

    public Path writeSummary(BreakoutStats stats, Path file) throws IOException {
        List<String> lines = new ArrayList<>();

        // Header
        List<String> header = new ArrayList<>(List.of("year", "judged_days", "outside_universe",
                "no_flag", "evaluated", "signals", "signals_risk_on"));
        for (BreakoutCriterion c : BreakoutCriterion.values()) {
            header.add("failed_" + c.name());
            header.add("near_miss_" + c.name());
        }
        lines.add(String.join(SEP, header));

        // One row per year
        for (Map.Entry<Integer, YearStats> entry : stats.byYear().entrySet()) {
            YearStats y = entry.getValue();
            List<String> row = new ArrayList<>();
            row.add(String.valueOf(entry.getKey()));
            row.add(String.valueOf(y.judgedDays()));
            row.add(String.valueOf(y.outsideUniverse()));
            row.add(String.valueOf(y.noFlag()));
            row.add(String.valueOf(y.evaluated()));
            row.add(String.valueOf(y.signals()));
            row.add(String.valueOf(y.signalsRiskOn()));
            for (BreakoutCriterion c : BreakoutCriterion.values()) {
                row.add(String.valueOf(y.failed(c)));
                row.add(String.valueOf(y.nearMiss(c)));
            }
            lines.add(String.join(SEP, row));
        }

        Files.createDirectories(file.getParent());
        Files.write(file, lines);
        return file;
    }

    public Path writeSignals(BreakoutStats stats, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(SEP, "symbol", "date", "regime", "type", "failed_criterion"));

        for (BreakoutDay day : stats.interestingDays()) {
            String type = day.isSignal() ? "SIGNAL" : "NEAR_MISS";
            String failed = day.isNearMiss()
                    ? day.failed().iterator().next().name()
                    : "";

            lines.add(String.join(SEP,
                    day.symbol(),
                    day.date().toString(),
                    day.regime().name(),
                    type,
                    failed));
        }

        Files.createDirectories(file.getParent());
        Files.write(file, lines);
        return file;
    }
}