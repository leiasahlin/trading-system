package com.qullamaggie.tradingsystem.universe.service;

import com.qullamaggie.tradingsystem.data.dto.Quote;
import com.qullamaggie.tradingsystem.data.dto.SymbolInfo;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.universe.DiscoveryConfig;
import com.qullamaggie.tradingsystem.universe.UniverseFilterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Populates the stock table by screening the whole market, so the system finds
 * its own candidates rather than depending on a curated list.
 *
 * Runs in stages to keep API usage down: a symbol list per exchange (cheap),
 * then a quote per symbol for the coarse price/volume screen, then ADR only for
 * the survivors. The provider offers no screener endpoint, so the filtering is
 * done here.
 */
@Service
public class MarketDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MarketDiscoveryService.class);
    private static final int ADR_DAYS = 21;

    private final MarketDataProvider marketDataProvider;
    private final StockRepository stockRepository;
    private final IndicatorCalculator calculator;
    private final DiscoveryConfig config;
    private final UniverseFilterConfig universeConfig;

    public MarketDiscoveryService(MarketDataProvider marketDataProvider,
                                  StockRepository stockRepository,
                                  IndicatorCalculator calculator,
                                  DiscoveryConfig config,
                                  UniverseFilterConfig universeConfig) {
        this.marketDataProvider = marketDataProvider;
        this.stockRepository = stockRepository;
        this.calculator = calculator;
        this.config = config;
        this.universeConfig = universeConfig;
    }

    public void discoverAndSaveCandidates() {
        int added = 0;
        for (String exchange : config.exchanges()) {
            for (SymbolInfo symbol : marketDataProvider.fetchSymbols(exchange)) {
                if (shouldConsider(symbol) && passesCoarseScreen(symbol.symbol())
                        && hasEnoughAdr(symbol.symbol())) {
                    save(symbol);
                    added++;
                }
            }
        }
        log.info("Marknadsgenomsökning klar: {} nya aktier tillagda", added);
    }

    private boolean shouldConsider(SymbolInfo symbol) {
        return config.instrumentTypes().contains(symbol.type())
                && !stockRepository.existsBySymbol(symbol.symbol());
    }

    /**
     * Price and single-day volume. The volume bar is deliberately lower than the
     * real requirement: one quiet day shouldn't disqualify a normally liquid stock.
     */
    private boolean passesCoarseScreen(String symbol) {
        Quote quote = marketDataProvider.fetchQuote(symbol);
        if (quote == null) {
            return false;
        }
        long minVolume = (long) (universeConfig.minAvgVolume()
                * config.volumeToleranceFactor().doubleValue());
        return quote.close().compareTo(config.minPrice()) >= 0 && quote.volume() >= minVolume;
    }

    private boolean hasEnoughAdr(String symbol) {
        List<DailyPrice> prices = marketDataProvider.fetchDailyPrices(symbol, ADR_DAYS);
        if (prices.size() < ADR_DAYS) {
            return false;
        }
        BigDecimal adr = calculator.calculateADR(prices);
        return adr != null && adr.compareTo(universeConfig.minAdr()) >= 0;
    }

    private void save(SymbolInfo symbol) {
        Stock stock = new Stock();
        stock.setSymbol(symbol.symbol());
        stock.setName(symbol.name());
        stock.setIsin(symbol.isin());
        stockRepository.save(stock);
    }
}