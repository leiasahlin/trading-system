package com.qullamaggie.tradingsystem.alerts;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * Calculates position size based on risk per trade.
 * Pure math, no dependencies — mirrors IndicatorCalculator.
 */
@Component
public class PositionSizeCalculator {
    /**
     * Calculates how many shares to buy based on risk-based position sizing.
     *
     * @param accountSize total account capital
     * @param riskPercent fraction of account to risk per trade (e.g. 0.01 for 1%)
     * @param entryPrice  the intended entry price
     * @param stopPrice   the stop-loss price
     * @return the number of shares to buy
     */
    public int calculateShares(BigDecimal accountSize, BigDecimal riskPercent,
                               BigDecimal entryPrice, BigDecimal stopPrice) {
        BigDecimal riskAmount = accountSize.multiply(riskPercent);

        BigDecimal riskPerShare = entryPrice.subtract(stopPrice);

        if (riskPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        BigDecimal shares = riskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);

        return shares.intValue();
    }
}
