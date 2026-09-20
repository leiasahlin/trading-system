package com.qullamaggie.tradingsystem.market.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.MarketRegimeRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.MarketRegimeEvaluator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class MarketRegimeService {

    private final StockRepository stockRepository;
    private final IndicatorRepository indicatorRepository;
    private final MarketRegimeRepository marketRegimeRepository;
    private final MarketRegimeEvaluator evaluator;
    private final MarketRegimeConfig config;

    public MarketRegimeService(StockRepository stockRepository,
                               IndicatorRepository indicatorRepository,
                               MarketRegimeRepository marketRegimeRepository,
                               MarketRegimeEvaluator evaluator,
                               MarketRegimeConfig config) {
        this.stockRepository = stockRepository;
        this.indicatorRepository = indicatorRepository;
        this.marketRegimeRepository = marketRegimeRepository;
        this.evaluator = evaluator;
        this.config = config;
    }

    /** Evaluates and stores today's market regime. */
    public MarketRegime evaluateAndSave(LocalDate asOfDate) {
        Map<String, Boolean> verdicts = new LinkedHashMap<>();
        StringBuilder details = new StringBuilder();

        for (String symbol : config.indexes()) {
            Optional<Stock> stock = stockRepository.findBySymbol(symbol);
            if (stock.isEmpty()) {
                verdicts.put(symbol, false);
                details.append(symbol).append(": ingen aktie-rad; ");
                continue;
            }

            Optional<Indicator> indicator =
                    indicatorRepository.findTop1ByStockOrderByDateDesc(stock.get());
            if (indicator.isEmpty()) {
                verdicts.put(symbol, false);
                details.append(symbol).append(": ingen indikatordata; ");
                continue;
            }

            BigDecimal fastMa = maForPeriod(indicator.get(), config.maFastPeriod());
            BigDecimal slowMa = maForPeriod(indicator.get(), config.maSlowPeriod());
            boolean constructive = evaluator.isConstructive(fastMa, slowMa);

            verdicts.put(symbol, constructive);
            details.append(symbol).append(": MA").append(config.maFastPeriod())
                    .append("=").append(fastMa)
                    .append(", MA").append(config.maSlowPeriod())
                    .append("=").append(slowMa)
                    .append(constructive ? " (positive); " : " (negative); ");
        }

        RegimeStatus status = evaluator.evaluate(verdicts);

        MarketRegime regime = marketRegimeRepository.findByDate(asOfDate)
                .orElseGet(MarketRegime::new);
        regime.setDate(asOfDate);
        regime.setStatus(status);
        regime.setDetails(details.toString());
        regime.setCreatedAt(LocalDateTime.now());

        return marketRegimeRepository.save(regime);
    }

    /** The most recent stored regime, if any. */
    public Optional<MarketRegime> getLatest() {
        return marketRegimeRepository.findTop1ByOrderByDateDesc();
    }

    private BigDecimal maForPeriod(Indicator indicator, int period) {
        return switch (period) {
            case 10 -> indicator.getMa10();
            case 20 -> indicator.getMa20();
            case 50 -> indicator.getMa50();
            default -> null;
        };
    }
}
