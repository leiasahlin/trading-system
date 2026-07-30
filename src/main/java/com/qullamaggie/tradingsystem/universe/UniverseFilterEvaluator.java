package com.qullamaggie.tradingsystem.universe;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import org.springframework.stereotype.Component;

@Component
public class UniverseFilterEvaluator {

    private final UniverseFilterConfig config;

    public UniverseFilterEvaluator(UniverseFilterConfig config) {
        this.config = config;
    }

    /**
     * True if the stock meets the universe's minimum price, liquidity,
     * and volatility requirements per the source document's filter.
     */
    public boolean isEligible(DailyPrice latestPrice, Indicator latestIndicator) {
        boolean isCloseBigEnough = latestPrice.getClose().compareTo(config.minPrice()) >= 0;
        boolean isAvgVolumeEnough = latestIndicator.getVolumeAvg20() >= (config.minAvgVolume());
        boolean isAdrEnough = latestIndicator.getAdr20().compareTo(config.minAdr()) >= 0;

        return isCloseBigEnough && isAvgVolumeEnough && isAdrEnough;
    }
}
