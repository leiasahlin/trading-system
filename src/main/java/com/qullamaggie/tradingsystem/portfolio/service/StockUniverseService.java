package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.portfolio.StockAlreadyExistsException;
import com.qullamaggie.tradingsystem.universe.UniverseFilterConfig;
import com.qullamaggie.tradingsystem.universe.UniverseFilterEvaluator;
import com.qullamaggie.tradingsystem.universe.service.RelativeStrengthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class StockUniverseService {

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorRepository indicatorRepository;
    private final MarketDataService marketDataService;
    private final IndicatorService indicatorService;
    private final UniverseFilterEvaluator universeFilterEvaluator;
    private final RelativeStrengthService relativeStrengthService;
    private static final Logger log = LoggerFactory.getLogger(StockUniverseService.class);

    public StockUniverseService(StockRepository stockRepository, DailyPriceRepository dailyPriceRepository, IndicatorRepository indicatorRepository, MarketDataService marketDataService, IndicatorService indicatorService, UniverseFilterEvaluator universeFilterEvaluator, MarketDataProvider marketDataProvider, UniverseFilterConfig config, RelativeStrengthService relativeStrengthService) {
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.marketDataService = marketDataService;
        this.indicatorService = indicatorService;
        this.universeFilterEvaluator = universeFilterEvaluator;
        this.relativeStrengthService = relativeStrengthService;
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
        reEvaluateEligibility(stock, relativeStrengthService.findLeaders());

        return stock;
    }

    public void reEvaluateEligibility(Stock stock, Set<String> leaders) {
        if (stock.getType() == StockType.INDEX) {
            return;
        }

        Optional<DailyPrice> price = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);
        Optional<Indicator> indicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);

        if (price.isEmpty() || indicator.isEmpty()) {
            return;
        }

        boolean isEligible = universeFilterEvaluator.isEligible(price.get(), indicator.get())
                && leaders.contains(stock.getSymbol());
        stock.setEligible(isEligible);
        stockRepository.save(stock);
    }

    public void reEvaluateAllStocks() {
        Set<String> leaders = relativeStrengthService.findLeaders();
        List<Stock> stocks = stockRepository.findAll();

        for (Stock stock : stocks) {
            try {
                reEvaluateEligibility(stock, leaders);
            } catch (Exception e) {
                log.warn("Kunde inte omvärdera behörighet för {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
        log.info("Universum: {} av {} aktier behöriga",
                stockRepository.findByEligibleTrue().size(), stocks.size());
    }
}
