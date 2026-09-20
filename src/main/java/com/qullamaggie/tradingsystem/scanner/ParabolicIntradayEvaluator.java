package com.qullamaggie.tradingsystem.scanner;

import com.qullamaggie.tradingsystem.data.dto.IntradayBar;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Intraday entry triggers for the parabolic short, per the methodology: a break
 * below the opening range low, or a failed reclaim of VWAP. Bars are 5-minute,
 * oldest first, timestamped by bar start in New York time.
 */
@Component
public class ParabolicIntradayEvaluator {

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private final IndicatorCalculator calculator;
    private final ParabolicShortConfig config;

    public ParabolicIntradayEvaluator(IndicatorCalculator calculator, ParabolicShortConfig config) {
        this.calculator = calculator;
        this.config = config;
    }

    /** Lowest low among bars inside the opening range, or null if none yet. */
    public BigDecimal openingRangeLow(List<IntradayBar> bars) {
        return bars.stream()
                .filter(b -> !b.time().toLocalTime().isBefore(MARKET_OPEN)
                        && b.time().toLocalTime().isBefore(openingRangeEnd()))
                .map(IntradayBar::low)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** True if the latest completed bar, after the opening range, closed below its low. */
    public boolean hasBrokenBelowOpeningRangeLow(List<IntradayBar> bars) {
        BigDecimal orl = openingRangeLow(bars);
        if (orl == null || bars.isEmpty()) {
            return false;
        }
        IntradayBar last = bars.getLast();
        boolean afterOpeningRange = !last.time().toLocalTime().isBefore(openingRangeEnd());
        return afterOpeningRange && last.close().compareTo(orl) < 0;
    }

    /**
     * True on a failed VWAP reclaim: the previous bar closed below VWAP, the latest
     * bar reached up to VWAP but closed back below it. Uses today's cumulative VWAP
     * for both bars - a simplification, since VWAP was marginally different one bar
     * earlier.
     */
    public boolean hasFailedVwapReclaim(List<IntradayBar> bars) {
        if (bars.size() < 2) {
            return false;
        }
        BigDecimal vwap = calculator.calculateVWAP(bars);
        if (vwap == null) {
            return false;
        }
        IntradayBar previous = bars.get(bars.size() - 2);
        IntradayBar last = bars.getLast();
        return previous.close().compareTo(vwap) < 0
                && last.high().compareTo(vwap) >= 0
                && last.close().compareTo(vwap) < 0;
    }

    private LocalTime openingRangeEnd() {
        return MARKET_OPEN.plusMinutes(config.openingRangeMinutes());
    }
}