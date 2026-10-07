package com.qullamaggie.tradingsystem.universe;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Relative strength ranking. The source scans for the 1-2% of stocks that have
 * gained the most over 1, 3 and 6 months. That figure applies to the whole
 * market; this ranks a universe that is already screened, so the percentile is
 * wider.
 */
@ConfigurationProperties(prefix = "trading.universe.relative-strength")
public record RelativeStrengthConfig(
        boolean enabled,
        List<Integer> lookbackDays,
        BigDecimal topPercentile
) {}