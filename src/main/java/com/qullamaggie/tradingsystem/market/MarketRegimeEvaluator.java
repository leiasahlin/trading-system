package com.qullamaggie.tradingsystem.market;

import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Determines the market regime from moving averages on the configured indexes.
 * The methodology only trades long setups when the fast MA sits above the slow
 * MA on the major indexes.
 */
@Component
public class MarketRegimeEvaluator {

    private final MarketRegimeConfig config;

    public MarketRegimeEvaluator(MarketRegimeConfig config) {
        this.config = config;
    }

    /** True if the fast MA is above the slow MA for this index. */
    public boolean isConstructive(BigDecimal fastMa, BigDecimal slowMa) {
        if (fastMa == null || slowMa == null) {
            return false;
        }
        return fastMa.compareTo(slowMa) > 0;
    }

    /**
     * Combines per-index verdicts according to the configured mode.
     * Missing data counts as not constructive, so an index without
     * indicators can never make the regime look better than it is.
     *
     * @param verdicts symbol to constructive-or-not
     */
    public RegimeStatus evaluate(Map<String, Boolean> verdicts) {
        if (verdicts.isEmpty()) {
            return RegimeStatus.RISK_OFF;
        }

        boolean constructive = config.regimeMode() == RegimeMode.ALL
                ? verdicts.values().stream().allMatch(v -> v)
                : verdicts.values().stream().anyMatch(v -> v);

        return constructive ? RegimeStatus.RISK_ON : RegimeStatus.RISK_OFF;
    }
}
