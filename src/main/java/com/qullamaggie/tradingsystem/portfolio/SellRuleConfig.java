package com.qullamaggie.tradingsystem.portfolio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.sellrules")
public record SellRuleConfig(
        BigDecimal minTrimR,
        int minDaysHeld,
        int maxDaysHeld,
        int maPeriod
) {
    public SellRuleConfig {
        if (maPeriod != 10 && maPeriod != 20) {
            throw new IllegalArgumentException("maPeriod måste vara 10 eller 20, var: " + maPeriod);

        }
    }
}
