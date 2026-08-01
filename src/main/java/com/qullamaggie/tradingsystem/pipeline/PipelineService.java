package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.portfolio.service.PositionMonitoringService;
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
    private final PositionMonitoringService positionMonitoringService;

    public PipelineService(MarketDataService marketDataService,
                           IndicatorService indicatorService, ScanService scanService,
                           AlertService alertService, StockUniverseService stockUniverseService, PositionMonitoringService positionMonitoringService) {
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.scanService = scanService;
        this.alertService = alertService;
        this.stockUniverseService = stockUniverseService;
        this.positionMonitoringService = positionMonitoringService;
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
        positionMonitoringService.monitorAllOpenPositions();
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

    /**
     * Episodic pivot scan without a redundant daily data refresh - relies on
     * yesterday's already-fetched daily data plus this run's own live
     * intraday snapshot per stock.
     */
    public void runEpisodicPivotScanOnly() {
        scanService.scanAllStocksForEpisodicPivot();
        alertService.createAlertsForAllScans();
    }
}
