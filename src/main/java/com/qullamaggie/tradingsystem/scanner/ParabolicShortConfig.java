package com.qullamaggie.tradingsystem.scanner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
        BigDecimal stopMarginPercent,
        int openingRangeMinutes,
        int marketCapMaxAgeDays,
        // Av som standard: positionsbevakningen räknar bara för long, så blankningar
        // skulle få felaktiga säljvarningar. Slås på med trading.scanner.parabolic.enabled=true
        @DefaultValue("false") boolean enabled
) {}
