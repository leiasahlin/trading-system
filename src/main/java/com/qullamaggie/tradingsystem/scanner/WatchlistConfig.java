package com.qullamaggie.tradingsystem.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "trading.watchlist")
public record WatchlistConfig(
    @DefaultValue("5") int minPassedCriteria,
    @DefaultValue("10") int maxCandidates
){}
