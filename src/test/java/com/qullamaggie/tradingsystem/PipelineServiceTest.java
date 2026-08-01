package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.entity.MarketRegime;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import com.qullamaggie.tradingsystem.pipeline.PipelineService;
import com.qullamaggie.tradingsystem.portfolio.service.PositionMonitoringService;
import com.qullamaggie.tradingsystem.portfolio.service.StockUniverseService;
import com.qullamaggie.tradingsystem.scanner.service.ScanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PipelineServiceTest {

    @Mock private MarketDataService marketDataService;
    @Mock private IndicatorService indicatorService;
    @Mock private ScanService scanService;
    @Mock private AlertService alertService;
    @Mock private StockUniverseService stockUniverseService;
    @Mock private PositionMonitoringService positionMonitoringService;
    @Mock private MarketRegimeService marketRegimeService;

    @InjectMocks
    private PipelineService pipelineService;

    private MarketRegime regime(RegimeStatus status) {
        MarketRegime regime = new MarketRegime();
        regime.setDate(LocalDate.now());
        regime.setStatus(status);
        return regime;
    }

    @Test
    public void shouldRunAllStepsInOrder_whenRiskOn() {
        when(marketRegimeService.evaluateAndSave(any())).thenReturn(regime(RegimeStatus.RISK_ON));

        pipelineService.runForAllStocks();

        InOrder inOrder = inOrder(marketDataService, indicatorService, marketRegimeService,
                stockUniverseService, scanService, alertService, positionMonitoringService);
        inOrder.verify(marketDataService).refreshAllStocks();
        inOrder.verify(indicatorService).calculateForAllStocks();
        inOrder.verify(marketRegimeService).evaluateAndSave(any());
        inOrder.verify(stockUniverseService).reEvaluateAllStocks();
        inOrder.verify(scanService).scanAllStocks();
        inOrder.verify(scanService).scanAllStocksForParabolicShort();
        inOrder.verify(alertService).createAlertsForAllScans();
        inOrder.verify(positionMonitoringService).monitorAllOpenPositions();
    }

    @Test
    public void shouldSkipLongScans_butStillRunParabolicAndMonitor_whenRiskOff() {
        when(marketRegimeService.evaluateAndSave(any())).thenReturn(regime(RegimeStatus.RISK_OFF));

        pipelineService.runForAllStocks();

        verify(marketDataService).refreshAllStocks();
        verify(indicatorService).calculateForAllStocks();
        verify(stockUniverseService).reEvaluateAllStocks();

        // Regimen stoppar bara långa setuper
        verify(scanService, never()).scanAllStocks();

        // Parabolic är riktningsneutral och körs oavsett marknadsläge
        verify(scanService).scanAllStocksForParabolicShort();
        verify(alertService).createAlertsForAllScans();
        verify(positionMonitoringService).monitorAllOpenPositions();
    }

    @Test
    public void episodicPivotScanOnly_runsScan_whenStoredRegimeIsRiskOn() {
        when(marketRegimeService.getLatest()).thenReturn(Optional.of(regime(RegimeStatus.RISK_ON)));

        pipelineService.runEpisodicPivotScanOnly();

        verify(scanService).scanAllStocksForEpisodicPivot();
        verify(alertService).createAlertsForAllScans();
        // Ingen ny datauppdatering - poängen med den lättare metoden
        verifyNoInteractions(marketDataService, indicatorService);
    }

    @Test
    public void episodicPivotScanOnly_skipsScan_whenStoredRegimeIsRiskOff() {
        when(marketRegimeService.getLatest()).thenReturn(Optional.of(regime(RegimeStatus.RISK_OFF)));

        pipelineService.runEpisodicPivotScanOnly();

        verifyNoInteractions(scanService, alertService);
    }

    @Test
    public void episodicPivotScanOnly_skipsScan_whenNoRegimeStoredYet() {
        // Ingen bedömning alls - anta inte att marknaden är gynnsam
        when(marketRegimeService.getLatest()).thenReturn(Optional.empty());

        pipelineService.runEpisodicPivotScanOnly();

        verifyNoInteractions(scanService, alertService);
    }
}
