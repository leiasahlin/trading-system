package com.qullamaggie.tradingsystem.indicators;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading.indicators.phase")
public record PhaseDetectionConfig(int minFlagDays, int maxFlagDays, int flagpoleDays) {
    public PhaseDetectionConfig {
        if (minFlagDays >= maxFlagDays) {
            throw new IllegalArgumentException(
                    "minFlagDays måste vara mindre än maxFlagDays, var: "
                            + minFlagDays + " och " + maxFlagDays);
        }
    }
}