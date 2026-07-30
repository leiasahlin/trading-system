package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.portfolio.StockAlreadyExistsException;
import com.qullamaggie.tradingsystem.universe.UniverseFilterEvaluator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StockUniverseService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorRepository indicatorRepository;
    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;
    private final UniverseFilterEvaluator universeFilterEvaluator;

    public StockUniverseService(StockRepository stockRepository, DailyPriceRepository dailyPriceRepository, IndicatorRepository indicatorRepository, MarketDataService marketDataService, IndicatorService indicatorService, UniverseFilterEvaluator universeFilterEvaluator) {
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.universeFilterEvaluator = universeFilterEvaluator;
    }

    public Stock addStock(String symbol) {
        if (stockRepository.existsBySymbol(symbol)) {
            throw new StockAlreadyExistsException(symbol);
        }

        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stockRepository.save(stock);

        marketDataService.refreshPrices(stock);
        indicatorService.calculateAndSaveIndicators(stock);
        reEvaluateEligibility(stock);

        return stock;
    }

    public void reEvaluateEligibility(Stock stock) {
        Optional<DailyPrice> price = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);
        Optional<Indicator> indicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);

        if (price.isEmpty() || indicator.isEmpty()) {
            return;
        }

        boolean isEligible = universeFilterEvaluator.isEligible(price.get(), indicator.get());
        stock.setEligible(isEligible);
        stockRepository.save(stock);
    }

    public void reEvaluateAllStocks() {
        List<Stock> stock = stockRepository.findAll();

        for (Stock s : stock) {
            reEvaluateEligibility(s);
        }

    }
}
