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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

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
        log.info("Kvällspipeline startar");

        marketDataService.refreshAllStocks();
        log.info("Priser uppdaterade");

        indicatorService.calculateForAllStocks();
        log.info("Indikatorer beräknade");

        MarketRegime regime = marketRegimeService.evaluateAndSave(LocalDate.now());
        log.info("Marknadsregim: {}", regime.getStatus());

        stockUniverseService.reEvaluateAllStocks();
        log.info("Universum omvärderat");

        if (regime.getStatus() == RegimeStatus.RISK_ON) {
            scanService.scanAllStocks();
            log.info("Breakout-scan klar");
        } else {
            log.info("RISK_OFF - breakout-scan hoppas över");
        }
        scanService.scanAllStocksForParabolicShort();
        log.info("Parabolic-scan klar");

        alertService.createAlertsForAllScans();
        positionMonitoringService.monitorAllOpenPositions();
        log.info("Kvällspipeline klar");
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
     * Lighter run during market hours: only the EP scan and its alerts, no data
     * refresh. Runs in both regimes. In RISK_OFF the alerts get half the risk and
     * a tighter stop limit (handled in AlertService), since an EP is driven by
     * news rather than the market. Breakouts stay off in RISK_OFF.
     */
    public void runEpisodicPivotScanOnly() {
        boolean riskOn = marketRegimeService.getLatest()
                .map(regime -> regime.getStatus() == RegimeStatus.RISK_ON)
                .orElse(false);

        if (!riskOn) {
            log.info("RISK_OFF - EP-scan körs med halv risk och tajtare stopp");
        }

        scanService.scanAllStocksForEpisodicPivot();
        alertService.createAlertsForAllScans();
    }
}
