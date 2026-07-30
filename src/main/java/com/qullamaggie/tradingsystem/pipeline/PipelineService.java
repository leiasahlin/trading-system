package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.portfolio.service.StockUniverseService;
import com.qullamaggie.tradingsystem.scanner.service.ScanService;
import org.springframework.stereotype.Service;

/**
 * Runs the full pipeline for all tracked stocks:
 * refreshes price data, recalculates indicators, scans for setups,
 * then creates alerts for new matches.
 */
@Service
public class PipelineService {
    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;
    private final ScanService scanService;
    private final AlertService alertService;
    private final StockUniverseService stockUniverseService;

    public PipelineService(MarketDataService marketDataService,
                           IndicatorService indicatorService, ScanService scanService,
                           AlertService alertService, StockUniverseService stockUniverseService) {
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.scanService = scanService;
        this.alertService = alertService;
        this.stockUniverseService = stockUniverseService;
    }

    /**
     * Runs the full pipeline for all tracked stocks:
     * refreshes price data, then recalculates indicators.
     */
    public void runForAllStocks() {
        marketDataService.refreshAllStocks();
        indicatorService.calculateForAllStocks();
        stockUniverseService.reEvaluateAllStocks();
        scanService.scanAllStocks();
        alertService.createAlertsForAllScans();
    }

    /**
     * Runs the episodic pivot pipeline for all tracked stocks:
     * refreshes price data, recalculates indicators, scans for episodic pivots,
     * then creates alerts for new matches.
     */
    public void runEpisodicPivotScan() {
        marketDataService.refreshAllStocks();
        indicatorService.calculateForAllStocks();
        scanService.scanAllStocksForEpisodicPivot();
        alertService.createAlertsForAllScans();
    }
}
