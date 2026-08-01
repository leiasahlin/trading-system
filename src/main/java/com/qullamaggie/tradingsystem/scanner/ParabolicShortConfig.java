package com.qullamaggie.tradingsystem.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "trading.scanner.parabolic")
public record ParabolicShortConfig(
        BigDecimal minDailyGainPercent,
        BigDecimal minLargeCapMovePercent,
        BigDecimal minMidCapMovePercent,
        BigDecimal minSmallCapMovePercent,
        BigDecimal smallCapMaxUsd,
        BigDecimal largeCapMinUsd,
        int lookbackDays,
        int minConsecutiveUpDays,
        BigDecimal minBreakdownVolumeRatio,
        BigDecimal minChurnVolumeRatio,
        BigDecimal maxChurnPricePercent,
        BigDecimal stopMarginPercent
) {}
