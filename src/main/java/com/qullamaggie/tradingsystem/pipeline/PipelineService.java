package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.scanner.service.ScanService;
import org.springframework.stereotype.Service;

/**
 * Runs the full pipeline for all tracked stocks:
 * refreshes price data, recalculates indicators, then scans for setups.
 */
@Service
public class PipelineService {
    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;
    private final ScanService scanService;

    public PipelineService(MarketDataService marketDataService,
                           IndicatorService indicatorService, ScanService scanService) {
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.scanService = scanService;
    }

    /**
     * Runs the full pipeline fpr all tracked stocks:
     * refreshes price data, then recalculates indicators.
     */
    public void runForAllStocks() {
        marketDataService.refreshAllStocks();
        indicatorService.calculateForAllStocks();
        scanService.scanAllStocks();
    }
}
