package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.portfolio.service.IntradayMonitoringService;
import com.qullamaggie.tradingsystem.scanner.*;
import com.qullamaggie.tradingsystem.scanner.service.ParabolicIntradayService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class PipelineScheduler {
    private final PipelineService pipelineService;
    private final IntradayMonitoringService intradayMonitoringService;
    private final ParabolicIntradayService parabolicIntradayService;

    public PipelineScheduler(PipelineService pipelineService, IntradayMonitoringService intradayMonitoringService,
                             ParabolicIntradayService parabolicIntradayService) {
        this.pipelineService = pipelineService;
        this.intradayMonitoringService = intradayMonitoringService;
        this.parabolicIntradayService = parabolicIntradayService;
    }

    /**
     * Runs the full breakout + universe + position-monitoring pipeline once
     * daily after the US market close, with a buffer for Twelve Data to finalize
     * the day's bar.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/New_York")
    public void runEveningPipeline() {
        pipelineService.runForAllStocks();
    }

    /**
     * Runs the episodic pivot scan shortly after the US market open, once the
     * configured opening-range window has elapsed.
     */
    @Scheduled(cron = "0 45 9 * * MON-FRI", zone = "America/New_York")
    public void runMorningEpisodicPivot() {
        pipelineService.runEpisodicPivotScanOnly();
    }

    /**
     * Intraday stop watch: every 5 minutes during US market hours. The cron
     * covers 09:00-16:59; the in-method guard trims it to the actual session,
     * since cron can't express 09:35-16:00 directly.
     */
    @Scheduled(cron = "0 */5 9-16 * * MON-FRI", zone = "America/New_York")
    public void runIntradayStopWatch() {
        LocalTime now = LocalTime.now(ZoneId.of("America/New_York"));
        if (now.isBefore(LocalTime.of(9, 35)) || now.isAfter(LocalTime.of(16, 0))) {
            return;
        }
        intradayMonitoringService.checkAllOpenPositions();
    }

    @Scheduled(cron = "0 */5 9-16 * * MON-FRI", zone = "America/New_York")
    public void runIntradayLoop() {
        LocalTime now = LocalTime.now(ZoneId.of("America/New_York"));
        if (now.isBefore(LocalTime.of(9, 35)) || now.isAfter(LocalTime.of(16, 0))) {
            return;
        }
        intradayMonitoringService.checkAllOpenPositions();
        parabolicIntradayService.checkCandidates();
    }
}