package com.qullamaggie.tradingsystem.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.scanner.episodic-pivot")
public record EpisodicPivotScanConfig(
        BigDecimal minGap,
        BigDecimal minRelativeVolume,
        BigDecimal minVolumeVsYesterday,
        BigDecimal maxPriorMovePercent,
        @DefaultValue("126") int priorMoveDays,
        @DefaultValue("0.5") BigDecimal riskOffMaxStopAdr
) {}
