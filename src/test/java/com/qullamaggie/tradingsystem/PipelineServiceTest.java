package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.pipeline.PipelineService;
import com.qullamaggie.tradingsystem.scanner.service.ScanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
public class PipelineServiceTest {
    @Mock
    private MarketDataService marketDataService;
    @Mock
    private IndicatorService indicatorService;
    @Mock
    private ScanService scanService;
    @Mock
    private AlertService alertService;

    @InjectMocks
    private PipelineService pipelineService;

    @Test
    public void shouldRunAllStepsInOrder() {
        pipelineService.runForAllStocks();

        InOrder inOrder = inOrder(marketDataService, indicatorService, scanService, alertService);
        inOrder.verify(marketDataService).refreshAllStocks();
        inOrder.verify(indicatorService).calculateForAllStocks();
        inOrder.verify(scanService).scanAllStocks();
        inOrder.verify(alertService).createAlertsForAllScans();
    }
}
