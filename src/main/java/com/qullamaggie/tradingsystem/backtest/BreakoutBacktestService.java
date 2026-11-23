package com.qullamaggie.tradingsystem.backtest;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.entity.StockType;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Runs the breakout signal backtest: builds the historical market regime,
 * then judges every stock day by day and collects the results.
 */
@Service
public class BreakoutBacktestService {
    private static final Logger log = LoggerFactory.getLogger(BreakoutBacktestService.class);

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final RegimeHistory regimeHistory;
    private final StockHistoryEvaluator stockHistoryEvaluator;
    private final MarketRegimeConfig regimeConfig;

    public BreakoutBacktestService(StockRepository stockRepository,
                                   DailyPriceRepository dailyPriceRepository,
                                   RegimeHistory regimeHistory,
                                   StockHistoryEvaluator stockHistoryEvaluator,
                                   MarketRegimeConfig regimeConfig) {
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.regimeHistory = regimeHistory;
        this.stockHistoryEvaluator = stockHistoryEvaluator;
        this.regimeConfig = regimeConfig;
    }

    public BreakoutStats run(LocalDate from) {
        Instant started = Instant.now();

        // 1. Market regime per day, from the index
        String indexSymbol = regimeConfig.indexes().getFirst();
        Stock index = stockRepository.findBySymbol(indexSymbol)
                .orElseThrow(() -> new IllegalStateException(indexSymbol + " saknas i stocks"));
        List<DailyPrice> indexPrices = dailyPriceRepository.findByStockOrderByDateAsc(index);
        Map<LocalDate, RegimeStatus> regimes = regimeHistory.build(indexSymbol, indexPrices);

        // 2. Judge every tradeable stock day by day
        BreakoutStats stats = new BreakoutStats();
        List<Stock> stocks = stockRepository.findAll();
        int processed = 0;

        for (Stock stock : stocks) {
            if (stock.getType() == StockType.INDEX) {
                continue;
            }
            try {
                List<DailyPrice> prices = dailyPriceRepository.findByStockOrderByDateAsc(stock);
                List<BreakoutDay> days = stockHistoryEvaluator.evaluate(stock.getSymbol(), prices,
                        regimes, from);
                stats.recordAll(days);
            } catch (Exception e) {
                log.warn("Backtest misslyckades för {}: {}", stock.getSymbol(), e.getMessage());
            }
            if (++processed % 50 == 0) {
                log.info("Backtest: {}/{}", processed, stocks.size());
            }
        }

        log.info("Backtest klar på {} s, {} signaler och nästan-träffar",
                Duration.between(started, Instant.now()).toSeconds(),
                stats.interestingDays().size());
        return stats;
    }
}