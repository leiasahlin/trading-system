package com.qullamaggie.tradingsystem.backtest;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.MarketRegimeEvaluator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Market regime for every historical day, using only index prices
 * up to and including that day. Same rule as MarketRegimeService.
 */
@Component
public class RegimeHistory {

    private final IndicatorCalculator calculator;
    private final MarketRegimeEvaluator evaluator;
    private final MarketRegimeConfig config;

    public RegimeHistory(IndicatorCalculator calculator, MarketRegimeEvaluator evaluator,
                         MarketRegimeConfig config) {
        this.calculator = calculator;
        this.evaluator = evaluator;
        this.config = config;
    }

    /**
     * @param indexPrices oldest first
     */
    public Map<LocalDate, RegimeStatus> build(String symbol, List<DailyPrice> indexPrices) {
        Map<LocalDate, RegimeStatus> regimes = new HashMap<>();

        for (int i = config.maSlowPeriod() - 1; i < indexPrices.size(); i++) {
            List<DailyPrice> upToDay = indexPrices.subList(0, i + 1);

            BigDecimal fastMa = maOverLast(upToDay, config.maFastPeriod());
            BigDecimal slowMa = maOverLast(upToDay, config.maSlowPeriod());
            boolean constructive = evaluator.isConstructive(fastMa, slowMa);

            regimes.put(indexPrices.get(i).getDate(), evaluator.evaluate(Map.of(symbol, constructive)));
        }
        return regimes;
    }

    private BigDecimal maOverLast(List<DailyPrice> prices, int n) {
        List<BigDecimal> closes = prices.subList(prices.size() - n, prices.size())
                .stream().map(DailyPrice::getClose).toList();
        return calculator.calculateMA(closes);
    }
}