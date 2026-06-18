package com.qullamaggie.tradingsystem.pipeline;

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

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Triggers the full pipeline for all tracked stocks:
     * refreshes price data, then recalculates indicators.
     */
    @PostMapping("/run")
    public void run() {
        pipelineService.runForAllStocks();
    }
}
