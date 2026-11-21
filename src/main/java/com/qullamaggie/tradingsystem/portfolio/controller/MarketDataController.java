package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.data.dto.RefreshSummary;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering market data operations.
 */
@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {
    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * Triggers a refresh of price data for all tracked stocks.
     */
    @PostMapping("/refresh")
    public RefreshSummary refreshAll() {
        return marketDataService.refreshAllStocks();
    }

    /**
     * Re-fetches the full price history for every stock from history-start.
     * Run once after history-start is moved back. Takes a while.
     */
    @PostMapping("/reseed")
    public RefreshSummary reseedAll() {
        return marketDataService.reseedAllStocks();
    }
}
