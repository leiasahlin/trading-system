package com.qullamaggie.tradingsystem.universe.service;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.entity.StockType;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.universe.RelativeStrengthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Finds the market's current leaders: the stocks gaining the most over the
 * configured windows. A stock qualifies if it ranks in the top percentile of
 * at least one window, since leadership shows up over different horizons.
 *
 * Runs entirely on stored price history.
 */
@Service
public class RelativeStrengthService {

    private static final Logger log = LoggerFactory.getLogger(RelativeStrengthService.class);

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorCalculator calculator;
    private final RelativeStrengthConfig config;

    public RelativeStrengthService(StockRepository stockRepository,
                                   DailyPriceRepository dailyPriceRepository,
                                   IndicatorCalculator calculator,
                                   RelativeStrengthConfig config) {
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.calculator = calculator;
        this.config = config;
    }

    /** Symbols ranking in the top percentile of at least one window. */
    public Set<String> findLeaders() {
        if (!config.enabled()) {
            return Set.of();
        }

        List<Stock> stocks = stockRepository.findAll().stream()
                .filter(s -> s.getType() == StockType.TRADEABLE)
                .toList();

        Map<String, List<DailyPrice>> history = new HashMap<>();
        for (Stock stock : stocks) {
            history.put(stock.getSymbol(), dailyPriceRepository.findByStockOrderByDateAsc(stock));
        }

        Set<String> leaders = new HashSet<>();
        for (int window : config.lookbackDays()) {
            leaders.addAll(topPerformers(history, window));
        }

        log.info("Relativ styrka: {} av {} aktier är ledare", leaders.size(), stocks.size());
        return leaders;
    }

    private List<String> topPerformers(Map<String, List<DailyPrice>> history, int window) {
        record Ranked(String symbol, BigDecimal change) {}

        List<Ranked> ranked = new ArrayList<>();
        for (Map.Entry<String, List<DailyPrice>> entry : history.entrySet()) {
            List<DailyPrice> prices = entry.getValue();
            if (prices.size() < window) {
                continue;   // för kort historik för det här fönstret
            }
            BigDecimal change = calculator.calculatePriceChange(
                    prices.subList(prices.size() - window, prices.size()));
            if (change != null) {
                ranked.add(new Ranked(entry.getKey(), change));
            }
        }

        ranked.sort(Comparator.comparing(Ranked::change).reversed());
        int cutoff = (int) Math.ceil(ranked.size() * config.topPercentile().doubleValue());
        return ranked.stream().limit(cutoff).map(Ranked::symbol).toList();
    }
}