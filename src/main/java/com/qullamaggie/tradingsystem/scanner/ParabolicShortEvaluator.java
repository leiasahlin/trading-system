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

    /**
     * True if today's close broke below the 10-day EMA on elevated volume -
     * the source document's mechanical breakdown trigger.
     */
    public boolean hasBrokenBelowEma(BigDecimal latestClose, BigDecimal ema10,
                                     Long todayVolume, Long averageVolume) {
        if (latestClose == null || ema10 == null || todayVolume == null || averageVolume == null) {
            return false;
        }

        boolean brokeBelow = latestClose.compareTo(ema10) < 0;

        BigDecimal relativeVolume = indicatorCalculator.calculateRelativeVolume(todayVolume, averageVolume);
        boolean hasElevatedVolume = relativeVolume != null
                && relativeVolume.compareTo(config.minBreakdownVolumeRatio()) >= 0;

        return brokeBelow && hasElevatedVolume;
    }

    /**
     * True if the stock is churning: making new highs on extreme volume while
     * the price itself barely advances - supply meeting demand at the top.
     *
     * Note: the source document describes churning qualitatively ("avtagande
     * rörelse på nya prismässiga toppnivåer"). The thresholds applied here are
     * our quantification of that description, not derived from the source.
     *
     * @param prices oldest-first, the lookback window
     */
    public boolean isChurning(List<DailyPrice> prices, Long averageVolume) {
        if (prices.size() < 2 || averageVolume == null) {
            return false;
        }

        DailyPrice today = prices.getLast();
        DailyPrice yesterday = prices.get(prices.size() - 2);

        BigDecimal highestHigh = prices.getFirst().getHigh();
        for (DailyPrice p : prices) {
            highestHigh = highestHigh.max(p.getHigh());
        }
        boolean isAtNewHigh = today.getHigh().compareTo(highestHigh) == 0;

        BigDecimal relativeVolume = indicatorCalculator.calculateRelativeVolume(today.getVolume(), averageVolume);
        boolean hasExtremeVolume = relativeVolume != null
                && relativeVolume.compareTo(config.minChurnVolumeRatio()) >= 0;

        BigDecimal priceChange = today.getClose()
                .subtract(yesterday.getClose())
                .divide(yesterday.getClose(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .abs();
        boolean priceStalled = priceChange.compareTo(config.maxChurnPricePercent()) <= 0;

        return isAtNewHigh && hasExtremeVolume && priceStalled;
    }

    /**
     * Evaluates the complete Parabolic Short setup: extreme extension for the
     * stock's market cap, an accelerating run of consecutive up days, and a
     * technical breakdown trigger (EMA break on volume, or churning at highs).
     *
     * All three structural criteria must hold, plus at least one trigger -
     * the source document is explicit that entry happens ONLY on formal
     * technical evidence of breakdown, never on extension alone.
     *
     * @param prices oldest-first, already limited to the lookback window
     * @param marketCapUsd the company's market capitalisation
     * @param ema10 the 10-day EMA for the latest bar
     * @param averageVolume the average daily volume to measure surges against
     */
    public boolean isParabolicShort(List<DailyPrice> prices, BigDecimal marketCapUsd,
                                    BigDecimal ema10, Long averageVolume) {
        if (prices.isEmpty()) {
            return false;
        }

        boolean isExtended = isExtended(prices, marketCapUsd);
        if (!isExtended) {
            return false;
        }

        int upDays = countConsecutiveUpDays(prices);
        if (upDays < config.minConsecutiveUpDays()) {
            return false;
        }

        List<DailyPrice> upDayRun = consecutiveUpDayRun(prices);
        if (!isAccelerating(upDayRun)) {
            return false;
        }

        DailyPrice today = prices.getLast();
        boolean hasBreakdownTrigger =
                hasBrokenBelowEma(today.getClose(), ema10, today.getVolume(), averageVolume)
                        || isChurning(prices, averageVolume);

        return hasBreakdownTrigger;
    }

    /**
     * Returns the trailing run of consecutive up days as a sublist, including
     * the day immediately before the run so that the first day's percentage
     * change can be calculated.
     */
    private List<DailyPrice> consecutiveUpDayRun(List<DailyPrice> prices) {
        int upDays = countConsecutiveUpDays(prices);
        if (upDays == 0) {
            return List.of();
        }

        // +1 för dagen INNAN serien - isAccelerating behöver den som referenspunkt
        // för att kunna räkna ut första uppgångsdagens procentuella förändring.
        int fromIndex = prices.size() - upDays - 1;
        return prices.subList(fromIndex, prices.size());
    }
}
