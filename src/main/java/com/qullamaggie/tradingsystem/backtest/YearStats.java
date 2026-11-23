package com.qullamaggie.tradingsystem.backtest;

import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;

import java.util.EnumMap;
import java.util.Map;

/**
 * Counters for one year of judged stock-days.
 */
public class YearStats {

    private int judgedDays;
    private int outsideUniverse;
    private int noFlag;
    private int evaluated;
    private int signals;
    private int signalsRiskOn;

    // How often each criterion failed, and how often it was the only one that failed
    private final Map<BreakoutCriterion, Integer> failedCount = new EnumMap<>(BreakoutCriterion.class);
    private final Map<BreakoutCriterion, Integer> nearMissCount = new EnumMap<>(BreakoutCriterion.class);

    public void record(BreakoutDay day) {
        judgedDays++;

        switch (day.status()) {
            case OUTSIDE_UNIVERSE -> outsideUniverse++;
            case NO_FLAG -> noFlag++;
            case EVALUATED -> evaluated++;
        }

        if (day.isSignal()) {
            signals++;
            if (day.regime() == RegimeStatus.RISK_ON) {
                signalsRiskOn++;
            }
        }

        for (BreakoutCriterion criterion : day.failed()) {
            failedCount.merge(criterion, 1, Integer::sum);
        }

        if (day.isNearMiss()) {
            BreakoutCriterion only = day.failed().iterator().next();
            nearMissCount.merge(only, 1, Integer::sum);
        }
    }

    public int judgedDays() { return judgedDays; }
    public int outsideUniverse() { return outsideUniverse; }
    public int noFlag() { return noFlag; }
    public int evaluated() { return evaluated; }
    public int signals() { return signals; }
    public int signalsRiskOn() { return signalsRiskOn; }

    public int failed(BreakoutCriterion criterion) {
        return failedCount.getOrDefault(criterion, 0);
    }

    public int nearMiss(BreakoutCriterion criterion) {
        return nearMissCount.getOrDefault(criterion, 0);
    }
}