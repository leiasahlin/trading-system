package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.stereotype.Service;

import javax.swing.plaf.basic.BasicBorders;

/**
 * Orchestrates the full data analysis pipeline:
 * fetching price data, then recalculating indicators.
 */
@Service
public class PipelineService {
    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;

    public PipelineService(MarketDataService marketDataService,
                           IndicatorService indicatorService) {
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
    }

    /**
     * Runs the full pipeline fpr all tracked stocks:
     * refreshes price data, then recalculates indicators.
     */
    public void runForAllStocks() {
        marketDataService.refreshAllStocks();
        indicatorService.calculateForAllStocks();
    }
}
