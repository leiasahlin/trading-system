package com.qullamaggie.tradingsystem.indicators;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Splits price history into flagpole and flag by locating the peak the
 * consolidation hangs from: the highest high within the maximum flag window.
 * The days since that peak are the flag's actual length, rather than a fixed
 * window assumed in advance.
 */
@Component
public class PhaseDetector {

    private final PhaseDetectionConfig config;

    public PhaseDetector(PhaseDetectionConfig config) {
        this.config = config;
    }

    /**
     * @param prices oldest first
     * @return the two phases, or null if no valid flag is present
     */
    public FlagPhases detect(List<DailyPrice> prices) {
        if (prices.size() < config.maxFlagDays() + config.flagpoleDays()) {
            return null;
        }

        List<DailyPrice> searchWindow = prices.subList(
                prices.size() - config.maxFlagDays(), prices.size());

        int peakOffset = 0;
        BigDecimal highest = searchWindow.getFirst().getHigh();
        for (int i = 1; i < searchWindow.size(); i++) {
            if (searchWindow.get(i).getHigh().compareTo(highest) > 0) {
                highest = searchWindow.get(i).getHigh();
                peakOffset = i;
            }
        }

        int peakIndex = prices.size() - config.maxFlagDays() + peakOffset;
        int flagLength = prices.size() - peakIndex;

        // Toppen för nära: ingen konsolidering har hunnit bildas.
        // Toppen i fönstrets början: flaggan är troligen längre än tillåtet,
        // alltså ingen giltig formation.
        if (flagLength < config.minFlagDays() || peakOffset == 0) {
            return null;
        }

        int flagpoleStart = Math.max(0, peakIndex - config.flagpoleDays());
        return new FlagPhases(
                prices.subList(flagpoleStart, peakIndex),
                prices.subList(peakIndex, prices.size()));
    }
}