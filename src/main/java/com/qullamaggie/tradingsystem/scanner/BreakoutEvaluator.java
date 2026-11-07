package com.qullamaggie.tradingsystem.scanner;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Component
public class BreakoutEvaluator {

    private final BreakoutScanConfig config;

    public BreakoutEvaluator(BreakoutScanConfig config) {
        this.config = config;
    }

    public Optional<BreakoutEvaluation> evaluate(DailyPrice price, Indicator indicator) {
        if (indicator.getConsolidationRange() == null || indicator.getPullback() == null
                || indicator.getVolumeContraction() == null) {
            return Optional.empty();
        }

        Set<BreakoutCriterion> failed = EnumSet.noneOf(BreakoutCriterion.class);

        boolean hasPriorMove = indicator.getPriorMove().compareTo(config.minPriorMove()) >= 0
                && indicator.getPriorMove().compareTo(config.maxPriorMove()) <= 0;
        if (!hasPriorMove) {
            failed.add(BreakoutCriterion.PRIOR_MOVE);
        }

        boolean hasEnoughVolume = indicator.getVolumeAvg20() >= config.minAvgVolume();
        if (!hasEnoughVolume) {
            failed.add(BreakoutCriterion.AVG_VOLUME);
        }

        boolean hasEnoughAdr = indicator.getAdr20().compareTo(config.minAdr()) >= 0;
        if (!hasEnoughAdr) {
            failed.add(BreakoutCriterion.ADR);
        }

        boolean closedAboveShortMa = price.getClose().compareTo(indicator.getMa10()) > 0 ||   // close > ma10
                price.getClose().compareTo(indicator.getMa20()) > 0; // or close > ma20
        if (!closedAboveShortMa) {
            failed.add(BreakoutCriterion.CLOSED_ABOVE_SHORT_MA);
        }

        boolean hasTightConsolidation = indicator.getConsolidationRange()
                .compareTo(config.maxConsolidationRange()) <= 0;
        if (!hasTightConsolidation) {
            failed.add(BreakoutCriterion.CONSOLIDATION_RANGE);
        }

        boolean hasShallowPullback = indicator.getPullback().compareTo(config.maxPullback()) <= 0;
        if (!hasShallowPullback) {
            failed.add(BreakoutCriterion.PULLBACK);
        }

        boolean hasVolumeContraction = indicator.getVolumeContraction()
                .compareTo(config.maxVolumeContraction()) <= 0;
        if (!hasVolumeContraction) {
            failed.add(BreakoutCriterion.VOLUME_CONTRACTION);
        }

        BreakoutEvaluation breakoutEvaluation = new BreakoutEvaluation(failed);
        return Optional.of(breakoutEvaluation);
    }
}