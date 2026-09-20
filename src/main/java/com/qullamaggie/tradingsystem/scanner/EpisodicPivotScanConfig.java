package com.qullamaggie.tradingsystem.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.scanner.episodic-pivot")
public record EpisodicPivotScanConfig(
        BigDecimal minGap,
        BigDecimal minRelativeVolume,
        BigDecimal minVolumeVsYesterday
) {}
