package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.entity.MarketRegime;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import com.qullamaggie.tradingsystem.portfolio.service.PositionMonitoringService;
import com.qullamaggie.tradingsystem.portfolio.service.StockUniverseService;
import com.qullamaggie.tradingsystem.scanner.service.ScanService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

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
    private final MarketRegimeService marketRegimeService;

    public PipelineService(MarketDataService marketDataService,
                           IndicatorService indicatorService, ScanService scanService,
                           AlertService alertService, StockUniverseService stockUniverseService, PositionMonitoringService positionMonitoringService, MarketRegimeService marketRegimeService) {
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.scanService = scanService;
        this.alertService = alertService;
        this.stockUniverseService = stockUniverseService;
        this.positionMonitoringService = positionMonitoringService;
        this.marketRegimeService = marketRegimeService;
    }

    /**
     * Runs the full pipeline for all tracked stocks:
     * refreshes price data, then recalculates indicators.
     */
    public void runForAllStocks() {
        marketDataService.refreshAllStocks();
        indicatorService.calculateForAllStocks();
        MarketRegime regime = marketRegimeService.evaluateAndSave(LocalDate.now());
        stockUniverseService.reEvaluateAllStocks();

        if (regime.getStatus() == RegimeStatus.RISK_ON) {
            scanService.scanAllStocks();
        }
        scanService.scanAllStocksForParabolicShort();

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
        boolean riskOn = marketRegimeService.getLatest()
                .map(regime -> regime.getStatus() == RegimeStatus.RISK_ON)
                .orElse(false);

        if (!riskOn) {
            return;
        }

        scanService.scanAllStocksForEpisodicPivot();
        alertService.createAlertsForAllScans();
    }
}
