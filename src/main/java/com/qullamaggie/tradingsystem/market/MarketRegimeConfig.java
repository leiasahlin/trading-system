package com.qullamaggie.tradingsystem.market;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "trading.market")
public record MarketRegimeConfig(
        List<String> indexes,
        RegimeMode regimeMode,
        int maFastPeriod,
        int maSlowPeriod
) {
    private static final Set<Integer> SUPPORTED_MA_PERIODS = Set.of(10, 20, 50);

    public MarketRegimeConfig {

        if (indexes == null || indexes.isEmpty()) {
            throw new IllegalArgumentException("Minst ett index måste konfigureras");
        }
        if (maFastPeriod >= maSlowPeriod) {
            throw new IllegalArgumentException(
                    "maFastPeriod måste vara kortare än maSlowPeriod, var: "
                            + maFastPeriod + " och " + maSlowPeriod);
        }

        if (!SUPPORTED_MA_PERIODS.contains(maFastPeriod)
                || !SUPPORTED_MA_PERIODS.contains(maSlowPeriod)) {
            throw new IllegalArgumentException(
                    "MA-perioder måste vara någon av " + SUPPORTED_MA_PERIODS
                            + ", var: " + maFastPeriod + " och " + maSlowPeriod);
        }
    }
}