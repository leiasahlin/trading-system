package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.universe.service.MarketDiscoveryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering the full analysis pipeline.
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {
    private final PipelineService pipelineService;
    private final MarketDiscoveryService marketDiscoveryService;

    public PipelineController(PipelineService pipelineService, MarketDiscoveryService marketDiscoveryService) {
        this.pipelineService = pipelineService;
        this.marketDiscoveryService = marketDiscoveryService;
    }

    /**
     * Triggers the full pipeline for all tracked stocks:
     * refreshes price data, then recalculates indicators.
     */
    @PostMapping("/run")
    public void run() {
        pipelineService.runForAllStocks();
    }

    @PostMapping("/episodic-pivot")
    public void runEP() {
        pipelineService.runEpisodicPivotScan();
    }

    @PostMapping("/discovery")
    public void runDiscovery() {
        marketDiscoveryService.discoverAndSaveCandidates();
    }
}
