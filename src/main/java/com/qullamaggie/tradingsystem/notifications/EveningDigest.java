package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.PositionAlertType;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the evening email shows, collected by EveningDigestService and
 * turned into an email by EveningDigestFormatter. Kept as plain data so the
 * formatting can be tested without a database.
 *
 * The configs are included because the email explains the rules it applied
 * ("max 20 % of the account per stock") - the numbers come from the same
 * settings the system calculated with.
 *
 * @param regimeStatus null if no regime has been assessed yet
 */
public record EveningDigest(
        LocalDate date,
        RegimeStatus regimeStatus,
        RegimeMode regimeMode,
        List<IndexReading> indexReadings,
        AccountConfig account,
        SellRuleConfig sellRules,
        List<BuySignal> buySignals,
        List<PositionWarning> positionWarnings,
        List<OvernightWarning> overnightWarnings
) {
    /** True if anything in the email asks the user to do something. */
    public boolean hasActions() {
        return !buySignals.isEmpty() || !positionWarnings.isEmpty() || !overnightWarnings.isEmpty();
    }

    /** One market index's moving averages; the MAs are null if there was no data. */
    public record IndexReading(String symbol, int fastPeriod, BigDecimal fastMa,
                               int slowPeriod, BigDecimal slowMa) {}

    /**
     * @param riskPercent fraction of the account risked, e.g. 0.01; null on old alerts
     * @param adrPercent  the stock's ADR in percent, e.g. 6.1; null if no indicator data
     */
    public record BuySignal(long alertId, String symbol, SetupType setup,
                            BigDecimal entry, BigDecimal stop, int shares,
                            BigDecimal riskPercent, BigDecimal adrPercent) {}

    /**
     * @param suggestedStop the new stop for breakeven and trailing alerts, otherwise null
     * @param currentStop   the position's stop right now
     * @param summary       null if no price data was available
     * @param maValue       the trailing moving average; null if no indicator data
     */
    public record PositionWarning(long positionId, String symbol, SetupType setup, PositionAlertType type,
                                  BigDecimal suggestedStop, BigDecimal currentStop, BigDecimal initialRisk,
                                  long daysHeld, PositionSummary summary, BigDecimal maValue) {}

    public record OvernightWarning(String name, BigDecimal weightPercent, int sharesToSell) {}
}
