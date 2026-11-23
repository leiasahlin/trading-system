package com.qullamaggie.tradingsystem.backtest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private static final Path RESULTS_DIR = Path.of("backtest-results");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm");

    private final BreakoutBacktestService backtestService;
    private final BacktestCsvWriter csvWriter;

    public BacktestController(BreakoutBacktestService backtestService, BacktestCsvWriter csvWriter) {
        this.backtestService = backtestService;
        this.csvWriter = csvWriter;
    }

    /**
     * Runs the breakout signal backtest and writes summary and signals CSV files.
     *
     * @param from first day to judge, e.g. 2020-01-02
     */
    @PostMapping("/breakout-signals")
    public BacktestRunResult runBreakoutSignals(
            @RequestParam(defaultValue = "2020-01-02")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) throws IOException {

        BreakoutStats stats = backtestService.run(from);

        String stamp = LocalDateTime.now().format(STAMP);
        Path summary = csvWriter.writeSummary(stats, RESULTS_DIR.resolve("summary-" + stamp + ".csv"));
        Path signals = csvWriter.writeSignals(stats, RESULTS_DIR.resolve("signals-" + stamp + ".csv"));

        return new BacktestRunResult(
                stats.interestingDays().size(),
                summary.toAbsolutePath().toString(),
                signals.toAbsolutePath().toString());
    }

    /** What the endpoint answers with, shown in PowerShell. */
    public record BacktestRunResult(int signalsAndNearMisses, String summaryFile, String signalsFile) {}
}