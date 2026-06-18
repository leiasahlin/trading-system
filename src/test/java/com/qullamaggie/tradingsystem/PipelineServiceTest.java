package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.pipeline.PipelineService;
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

    @InjectMocks
    private PipelineService pipelineService;

    @Test
    public void shouldRefreshPricesThenCalculateIndicators() {
        pipelineService.runForAllStocks();

        InOrder inOrder = inOrder(marketDataService, indicatorService);
        inOrder.verify(marketDataService).refreshAllStocks();
        inOrder.verify(indicatorService).calculateForAllStocks();
    }
}
