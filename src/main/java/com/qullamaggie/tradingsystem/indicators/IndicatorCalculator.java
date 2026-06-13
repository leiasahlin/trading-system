package com.qullamaggie.tradingsystem.indicators;

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
}
