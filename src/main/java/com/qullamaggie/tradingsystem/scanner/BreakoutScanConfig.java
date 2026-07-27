package com.qullamaggie.tradingsystem.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.scanner.breakout")
public record BreakoutScanConfig(
        BigDecimal minPriorMove,
        BigDecimal maxPriorMove,
        BigDecimal minAdr,
        long minAvgVolume,
        BigDecimal maxConsolidationRange,
        BigDecimal maxPullback,
        BigDecimal maxVolumeContraction
) {}
