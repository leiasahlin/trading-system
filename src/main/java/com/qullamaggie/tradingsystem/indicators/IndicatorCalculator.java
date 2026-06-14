package com.qullamaggie.tradingsystem.indicators;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
}
