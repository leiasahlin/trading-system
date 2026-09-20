package com.qullamaggie.tradingsystem.universe;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.universe")
public record UniverseFilterConfig(
        BigDecimal minPrice,
        long minAvgVolume,
        BigDecimal minAdr,
        int marketCapMaxAgeDays
) {}
