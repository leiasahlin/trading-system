package com.qullamaggie.tradingsystem.data;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

@ConfigurationProperties(prefix = "trading.data")
public record MarketDataConfig(LocalDate historyStart) {
    public MarketDataConfig {
        if (historyStart == null) {
            throw new IllegalArgumentException(
                    "trading.data.history-start saknas i application.properties");
        }
    }
}