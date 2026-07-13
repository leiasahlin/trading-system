package com.qullamaggie.tradingsystem.indicators;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static java.util.Arrays.stream;

@Component
public class IndicatorCalculator {

    /**
     * Calculates a simple moving average (SMA) from a list of closing prices.
     *
     * @param closes the closing prices to average
     * @return the average, or null if the list is empty
     */
    public BigDecimal calculateMA(List<BigDecimal> closes) {
        if (closes.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;

        for (BigDecimal close : closes) {
            sum = sum.add(close);
        }
        return sum.divide(BigDecimal.valueOf(closes.size()), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateADR(List<DailyPrice> allPrices) {
        if (allPrices.size() < 2) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;

        for (int i = 1; i < allPrices.size(); i++) {
            BigDecimal high = allPrices.get(i).getHigh();
            BigDecimal low = allPrices.get(i).getLow();
            BigDecimal yesterdayClose = allPrices.get(i - 1).getClose();

            BigDecimal todayRange = high.subtract(low).divide(yesterdayClose, 4, RoundingMode.HALF_UP);

            sum = sum.add(todayRange);
            amount = amount.add(BigDecimal.ONE);
        }
        BigDecimal adr = sum.divide(amount, 4, RoundingMode.HALF_UP);

        return adr.multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal calculateATR(List<DailyPrice> allPrices) {
        if (allPrices.size() < 2) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;

        for (int i = 1; i < allPrices.size(); i++) {
            BigDecimal high = allPrices.get(i).getHigh();
            BigDecimal low = allPrices.get(i).getLow();
            BigDecimal yesterdayClose = allPrices.get(i - 1).getClose();

            BigDecimal dailyRange = high.subtract(low);
            BigDecimal gapUp = high.subtract(yesterdayClose);
            BigDecimal gapDown = yesterdayClose.subtract(low);

            BigDecimal trueRange = dailyRange.max(gapUp).max(gapDown);

            sum = sum.add(trueRange);
            amount = amount.add(BigDecimal.ONE);
        }
        BigDecimal atr = sum.divide(amount, 4, RoundingMode.HALF_UP);
        return atr;
    }

    public Long calculateAverageVolume(@NonNull List<Long> volumes) {
        if (volumes.isEmpty()) {
            return null;
        }

        Long sum = 0L;

        for (Long volume : volumes) {
            sum += volume;
        }

        return sum / volumes.size();
    }

    public BigDecimal findPriorMove(List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            return null;
        }

        BigDecimal lowestPrice = prices.getFirst().getClose();
        BigDecimal biggestMove = BigDecimal.ZERO;

        for (DailyPrice price : prices) {
            BigDecimal close = price.getClose();

            BigDecimal rise = close.subtract(lowestPrice).divide(lowestPrice, 4, RoundingMode.HALF_UP);
            if (rise.compareTo(biggestMove) > 0) {
                biggestMove = rise;
            }
            if (close.compareTo(lowestPrice) < 0) {
                lowestPrice = close;
            }
        }
        return biggestMove.multiply(BigDecimal.valueOf(100));
    }

    public ConsolidationResult calculateConsolidation(List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            return null;
        }
        BigDecimal highest = prices.getFirst().getHigh();
        BigDecimal lowest = prices.getFirst().getLow();

        for (DailyPrice p : prices) {
            highest = highest.max(p.getHigh());
            lowest = lowest.min(p.getLow());
        }

        BigDecimal range = highest.subtract(lowest).divide(lowest, 4,RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return new ConsolidationResult(highest, lowest, range);
    }

    public BigDecimal calculateGap(BigDecimal yesterdayClose, BigDecimal todayOpen) {
        if (yesterdayClose.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal gap = todayOpen.subtract(yesterdayClose).divide(yesterdayClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return gap;
    }

    public BigDecimal calculateRelativeVolume(Long todayVolume, Long averageVolume) {
        if (averageVolume == 0) {
            return null;
        }

        BigDecimal today = BigDecimal.valueOf(todayVolume);
        BigDecimal average = BigDecimal.valueOf(averageVolume);

        return today.divide(average, 4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePullback(List<DailyPrice> prices){
        if (prices.isEmpty()) {
            return null;
        }

        BigDecimal highest = prices.getFirst().getHigh();
        BigDecimal current = prices.getLast().getClose();

        for (DailyPrice p : prices) {
            highest = highest.max(p.getHigh());
        }

        BigDecimal pullback = (highest.subtract(current)).divide(highest, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        return pullback;
    }

    public BigDecimal calculateVolumeContraction(List<DailyPrice> flagPrices,
                                                 List<DailyPrice> flagpolePrices) {
        Long averageFlagVolume = calculateAverageVolume(flagPrices.stream().map(DailyPrice::getVolume).toList());
        Long averageFlagpoleVolume = calculateAverageVolume(flagpolePrices.stream().map(DailyPrice::getVolume).toList());

        if (averageFlagVolume == null || averageFlagpoleVolume == null || averageFlagpoleVolume == 0) {
            return null;
        }

        return BigDecimal.valueOf(averageFlagVolume).divide(BigDecimal.valueOf(averageFlagpoleVolume), 4, RoundingMode.HALF_UP);
    }
}
