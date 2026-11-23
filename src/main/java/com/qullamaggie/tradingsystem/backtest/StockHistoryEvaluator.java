package com.qullamaggie.tradingsystem.backtest;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.indicators.PhaseDetectionConfig;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluation;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluator;
import com.qullamaggie.tradingsystem.universe.UniverseFilterEvaluator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Walks one stock's history day by day and judges each day the way the
 * live system would have, using only prices up to and including that day.
 */
@Component
public class StockHistoryEvaluator {

    private final IndicatorService indicatorService;
    private final UniverseFilterEvaluator universeFilter;
    private final BreakoutEvaluator breakoutEvaluator;
    private final PhaseDetectionConfig phaseConfig;

    public StockHistoryEvaluator(IndicatorService indicatorService,
                                 UniverseFilterEvaluator universeFilter,
                                 BreakoutEvaluator breakoutEvaluator,
                                 PhaseDetectionConfig phaseConfig) {
        this.indicatorService = indicatorService;
        this.universeFilter = universeFilter;
        this.breakoutEvaluator = breakoutEvaluator;
        this.phaseConfig = phaseConfig;
    }

    /**
     * @param prices  oldest first
     * @param regimes market regime per date, from RegimeHistory
     * @param from    first day to judge; earlier days are only used as history
     */
    public List<BreakoutDay> evaluate(String symbol, List<DailyPrice> prices,
                                      Map<LocalDate, RegimeStatus> regimes, LocalDate from) {
        List<BreakoutDay> days = new ArrayList<>();

        // calculateIndicators needs 70 days, PhaseDetector needs maxFlag + flagpole
        int minHistory = Math.max(70, phaseConfig.maxFlagDays() + phaseConfig.flagpoleDays());

        for (int i = minHistory - 1; i < prices.size(); i++) {
            DailyPrice today = prices.get(i);
            if (today.getDate().isBefore(from)) {
                continue;
            }

            List<DailyPrice> upToDay = prices.subList(0, i + 1);
            Indicator indicator = indicatorService.calculateIndicators(upToDay);
            RegimeStatus regime = regimes.getOrDefault(today.getDate(), RegimeStatus.RISK_OFF);

            if (!universeFilter.isEligible(today, indicator)) {
                days.add(new BreakoutDay(symbol, today.getDate(), regime,
                        DayStatus.OUTSIDE_UNIVERSE, Set.of()));
                continue;
            }

            Optional<BreakoutEvaluation> evaluation = breakoutEvaluator.evaluate(today, indicator);
            if (evaluation.isEmpty()) {
                days.add(new BreakoutDay(symbol, today.getDate(), regime,
                        DayStatus.NO_FLAG, Set.of()));
                continue;
            }

            days.add(new BreakoutDay(symbol, today.getDate(), regime,
                    DayStatus.EVALUATED, evaluation.get().failed()));
        }
        return days;
    }
}