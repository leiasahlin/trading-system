package com.qullamaggie.tradingsystem.universe;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "trading.discovery")
public record DiscoveryConfig(
        List<String> exchanges,
        List<String> instrumentTypes,
        BigDecimal minPrice,
        BigDecimal volumeToleranceFactor,
        List<String> excludedSectors
) {}