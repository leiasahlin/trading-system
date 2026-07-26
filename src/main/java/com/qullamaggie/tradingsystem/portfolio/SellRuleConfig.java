package com.qullamaggie.tradingsystem.portfolio;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.sellrules")
public record SellRuleConfig(
        BigDecimal minTrimR,
        int minDaysHeld,
        int maxDaysHeld,
        int maPeriod
) {}
