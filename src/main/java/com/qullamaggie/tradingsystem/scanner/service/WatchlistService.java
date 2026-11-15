package com.qullamaggie.tradingsystem.scanner.service;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluation;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluator;
import com.qullamaggie.tradingsystem.scanner.WatchlistCandidate;
import com.qullamaggie.tradingsystem.scanner.WatchlistConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class WatchlistService {
    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorRepository indicatorRepository;
    private final BreakoutEvaluator breakoutEvaluator;
    private final WatchlistConfig config;

    public WatchlistService(StockRepository stockRepository, DailyPriceRepository dailyPriceRepository,
                            IndicatorRepository indicatorRepository, BreakoutEvaluator breakoutEvaluator,
                            WatchlistConfig config) {

        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.breakoutEvaluator = breakoutEvaluator;
        this.config = config;
    }

    private Optional<WatchlistCandidate> toCandidate(Stock stock) {
        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        Optional<DailyPrice> latestPrice = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);
        if (latestIndicator.isEmpty() || latestPrice.isEmpty()) {
            return Optional.empty();
        }

        DailyPrice price = latestPrice.get();
        Indicator indicator = latestIndicator.get();

        Optional<BreakoutEvaluation> evaluation = breakoutEvaluator.evaluate(price, indicator);
        if (evaluation.isEmpty()) {
            return Optional.empty();
        }

        if (evaluation.get().passedCount() < config.minPassedCriteria()) {
            return Optional.empty();
        }

        BigDecimal breakoutLevel = indicator.getConsolidationHigh();
        if (breakoutLevel == null) {
            return Optional.empty();
        }

        BigDecimal close = price.getClose();
        BigDecimal distance = breakoutLevel.subtract(close)
                .divide(close, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return Optional.of(new WatchlistCandidate(stock, price, indicator, evaluation.get(), distance));
    }

    /**
     * Stocks that meet most breakout criteria, the best first, so the evening email
     * can show what is building up before it triggers.
     *
     * @param excludedSymbols stocks that already have a buy signal today,
     *                        so they don't show up twice in the same email
     */
    public List<WatchlistCandidate> findTopCandidates(Set<String> excludedSymbols) {
        List<WatchlistCandidate> candidates = new ArrayList<>();

        for (Stock stock : stockRepository.findByEligibleTrue()) {

            if (excludedSymbols.contains(stock.getSymbol())) {
                continue;
            }

            try {
                Optional<WatchlistCandidate> candidate = toCandidate(stock);
                if (candidate.isPresent()) {
                    candidates.add(candidate.get());
                }
            } catch (Exception e) {
                log.warn("Kunde inte bedöma {} för bevakningslistan: {}", stock.getSymbol(), e.getMessage());
            }
        }

        candidates.sort(Comparator
                .comparing((WatchlistCandidate c) -> c.evaluation().passedCount()).reversed()
                .thenComparing(c -> c.distanceToBreakoutPercent().abs()));

        log.info("Bevakningslista: {} kandidater, visar max {}", candidates.size(), config.maxCandidates());

        return candidates.stream()
                .limit(config.maxCandidates())
                .toList();
    }
}
