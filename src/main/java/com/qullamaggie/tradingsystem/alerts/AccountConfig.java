package com.qullamaggie.tradingsystem.alerts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Account-level settings for position sizing. Risk per trade varies with the
 * market regime: the source specifies a defensive default and an offensive
 * level when conditions are constructive, never above the offensive ceiling.
 */
@ConfigurationProperties(prefix = "trading.account")
public record AccountConfig(
        BigDecimal size,
        BigDecimal maxPositionPercent,
        BigDecimal riskPercentDefensive,
        BigDecimal riskPercentOffensive
) {
    public AccountConfig {
        if (riskPercentOffensive.compareTo(riskPercentDefensive) < 0) {
            throw new IllegalArgumentException(
                    "riskPercentOffensive får inte vara lägre än riskPercentDefensive, var: "
                            + riskPercentOffensive + " och " + riskPercentDefensive);
        }
    }
}
