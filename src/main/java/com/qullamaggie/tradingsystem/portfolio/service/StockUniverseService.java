package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.portfolio.StockAlreadyExistsException;
import com.qullamaggie.tradingsystem.universe.UniverseFilterConfig;
import com.qullamaggie.tradingsystem.universe.UniverseFilterEvaluator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    private final MarketDataProvider marketDataProvider;
    private final UniverseFilterConfig config;

    public StockUniverseService(StockRepository stockRepository, DailyPriceRepository dailyPriceRepository, IndicatorRepository indicatorRepository, MarketDataService marketDataService, IndicatorService indicatorService, UniverseFilterEvaluator universeFilterEvaluator, MarketDataProvider marketDataProvider, UniverseFilterConfig config) {
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.universeFilterEvaluator = universeFilterEvaluator;
        this.marketDataProvider = marketDataProvider;
        this.config = config;
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
        if (stock.getType() == StockType.INDEX) {
            return;
        }

        refreshMarketCapIfStale(stock, LocalDate.now());

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

    private void refreshMarketCapIfStale(Stock stock, LocalDate asOfDate) {
        LocalDate lastUpdated = stock.getMarketCapUpdatedAt();

        boolean neverFetched = lastUpdated == null;
        boolean isStale = !neverFetched
                && ChronoUnit.DAYS.between(lastUpdated, asOfDate) >= config.marketCapMaxAgeDays();

        if (!neverFetched && !isStale) {
            return;
        }

        // TODO 2: marketDataProvider.fetchMarketCap(stock.getSymbol())
        BigDecimal marketCap = marketDataProvider.fetchMarketCap(stock.getSymbol());

        if (marketCap == null) {
            return;
        }

        stock.setMarketCapUsd(marketCap);
        stock.setMarketCapUpdatedAt(asOfDate);
    }
}
