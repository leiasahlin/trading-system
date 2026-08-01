package com.qullamaggie.tradingsystem.pipeline;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PipelineScheduler {
    private final PipelineService pipelineService;

    public PipelineScheduler(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
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
}