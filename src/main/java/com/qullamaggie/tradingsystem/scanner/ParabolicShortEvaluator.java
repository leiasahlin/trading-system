package com.qullamaggie.tradingsystem.scanner;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class ParabolicShortEvaluator {

    private final ParabolicShortConfig config;
    private final IndicatorCalculator indicatorCalculator;

    public ParabolicShortEvaluator(ParabolicShortConfig config, IndicatorCalculator indicatorCalculator) {
        this.config = config;
        this.indicatorCalculator = indicatorCalculator;
    }

    /**
     * Counts consecutive up days ending at the most recent price.
     *
     * @param prices oldest-first
     */
    public int countConsecutiveUpDays(List<DailyPrice> prices) {
        int count = 0;

        for (int i = prices.size() - 1; i > 0; i--) {
            BigDecimal today = prices.get(i).getClose();
            BigDecimal yesterday = prices.get(i - 1).getClose();

            if (today.compareTo(yesterday) <= 0) {
                break;
            }
            count++;
        }
        return count;
    }

    /**
     * True if each day's percentage gain exceeds the previous day's and meets
     * the configured minimum - the "increasingly vertical" slope the
     * methodology describes.
     *
     * @param prices oldest-first, the consecutive up-day run
     */
    public boolean isAccelerating(List<DailyPrice> prices) {
        if (prices.size() < 3) {
            return false;
        }

        List<BigDecimal> dailyChanges = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal today = prices.get(i).getClose();
            BigDecimal yesterday = prices.get(i - 1).getClose();

            BigDecimal change = today.subtract(yesterday)
                    .divide(yesterday, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            dailyChanges.add(change);
        }

        for (int i = 0; i < dailyChanges.size(); i++) {
            BigDecimal change = dailyChanges.get(i);

            if (change.compareTo(config.minDailyGainPercent()) < 0) {
                return false;
            }
            if (i > 0 && change.compareTo(dailyChanges.get(i - 1)) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the stock has extended far enough over the lookback window to
     * qualify as parabolic. Threshold depends on market cap - small caps
     * require a far larger move than large caps, per the source document.
     *
     * @param prices oldest-first, already limited to the lookback window
     * @param marketCapUsd the company's market capitalization
     */
    public boolean isExtended(List<DailyPrice> prices, BigDecimal marketCapUsd) {
        if (prices.isEmpty() || marketCapUsd == null) {
            return false;
        }

        BigDecimal move = indicatorCalculator.findPriorMove(prices);
        if (move == null) {
            return false;
        }

        return move.compareTo(requiredMoveFor(marketCapUsd)) >= 0;
    }

    private BigDecimal requiredMoveFor(BigDecimal marketCapUsd) {
        if (marketCapUsd.compareTo(config.smallCapMaxUsd()) < 0) {
            return config.minSmallCapMovePercent();
        }
        if (marketCapUsd.compareTo(config.largeCapMinUsd()) < 0) {
            return config.minMidCapMovePercent();
        }
        return config.minLargeCapMovePercent();
    }

    private boolean isSmallCap(BigDecimal marketCapUsd) {
        return marketCapUsd.compareTo(config.smallCapMaxUsd()) < 0;
    }
}
