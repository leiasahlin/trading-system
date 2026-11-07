package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluation;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluator;
import com.qullamaggie.tradingsystem.scanner.BreakoutScanConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each test starts from an indicator that passes everything and breaks one
 * thing, so a failure points straight at the criterion that changed.
 */
class BreakoutEvaluatorTest {

    private BreakoutEvaluator evaluator;
    private Indicator indicator;
    private DailyPrice price;

    @BeforeEach
    void setUp() {
        evaluator = new BreakoutEvaluator(new BreakoutScanConfig(
                new BigDecimal("30"),   // minPriorMove
                new BigDecimal("100"),  // maxPriorMove
                new BigDecimal("3"),    // minAdr
                1_000_000L,             // minAvgVolume
                new BigDecimal("15"),   // maxConsolidationRange
                new BigDecimal("25"),   // maxPullback
                new BigDecimal("0.8"))); // maxVolumeContraction

        indicator = new Indicator();
        indicator.setPriorMove(new BigDecimal("45"));
        indicator.setVolumeAvg20(2_000_000L);
        indicator.setAdr20(new BigDecimal("5"));
        indicator.setMa10(new BigDecimal("300"));
        indicator.setMa20(new BigDecimal("295"));
        indicator.setConsolidationRange(new BigDecimal("8"));
        indicator.setPullback(new BigDecimal("5"));
        indicator.setVolumeContraction(new BigDecimal("0.6"));

        price = new DailyPrice();
        price.setClose(new BigDecimal("310"));
    }

    /** Evaluates and fails the test if there was no answer at all. */
    private BreakoutEvaluation evaluate() {
        Optional<BreakoutEvaluation> result = evaluator.evaluate(price, indicator);
        assertTrue(result.isPresent(), "expected an evaluation, got Optional.empty()");
        return result.get();
    }

    // ---------- Allt uppfyllt ----------

    @Test
    void allCriteriaMet_nothingFailed() {
        BreakoutEvaluation evaluation = evaluate();

        assertTrue(evaluation.allPassed());
        assertEquals(7, evaluation.passedCount());
        assertTrue(evaluation.failed().isEmpty());
    }

    @Test
    void valuesExactlyAtTheLimits_pass() {
        // Gränserna är inklusiva: >= för minimum, <= för maximum
        indicator.setPriorMove(new BigDecimal("30"));
        indicator.setVolumeAvg20(1_000_000L);
        indicator.setAdr20(new BigDecimal("3"));
        indicator.setConsolidationRange(new BigDecimal("15"));
        indicator.setPullback(new BigDecimal("25"));
        indicator.setVolumeContraction(new BigDecimal("0.8"));

        assertTrue(evaluate().allPassed());
    }

    // ---------- Ett kriterium i taget ----------

    @Test
    void priorMoveTooSmall_failsPriorMove() {
        indicator.setPriorMove(new BigDecimal("20"));

        assertEquals(Set.of(BreakoutCriterion.PRIOR_MOVE), evaluate().failed());
    }

    @Test
    void priorMoveTooLarge_failsPriorMove() {
        // Över maxgränsen: rörelsen är redan för utsträckt
        indicator.setPriorMove(new BigDecimal("150"));

        assertEquals(Set.of(BreakoutCriterion.PRIOR_MOVE), evaluate().failed());
    }

    @Test
    void volumeTooLow_failsAvgVolume() {
        indicator.setVolumeAvg20(500_000L);

        assertEquals(Set.of(BreakoutCriterion.AVG_VOLUME), evaluate().failed());
    }

    @Test
    void adrTooLow_failsAdr() {
        indicator.setAdr20(new BigDecimal("2"));

        assertEquals(Set.of(BreakoutCriterion.ADR), evaluate().failed());
    }

    @Test
    void closeBelowBothMovingAverages_failsClosedAboveShortMa() {
        price.setClose(new BigDecimal("290"));   // under både MA10 (300) och MA20 (295)

        assertEquals(Set.of(BreakoutCriterion.CLOSED_ABOVE_SHORT_MA), evaluate().failed());
    }

    @Test
    void closeAboveMa20ButBelowMa10_stillPasses() {
        // Villkoret är MA10 ELLER MA20 - en räcker
        price.setClose(new BigDecimal("297"));   // under MA10 (300), över MA20 (295)

        assertTrue(evaluate().allPassed());
    }

    @Test
    void consolidationTooWide_failsConsolidationRange() {
        indicator.setConsolidationRange(new BigDecimal("20"));

        assertEquals(Set.of(BreakoutCriterion.CONSOLIDATION_RANGE), evaluate().failed());
    }

    @Test
    void pullbackTooDeep_failsPullback() {
        indicator.setPullback(new BigDecimal("30"));

        assertEquals(Set.of(BreakoutCriterion.PULLBACK), evaluate().failed());
    }

    @Test
    void volumeNotContracted_failsVolumeContraction() {
        indicator.setVolumeContraction(new BigDecimal("0.9"));

        assertEquals(Set.of(BreakoutCriterion.VOLUME_CONTRACTION), evaluate().failed());
    }

    // ---------- Flera på en gång ----------

    @Test
    void twoCriteriaFail_bothReported_fivePassed() {
        indicator.setAdr20(new BigDecimal("2"));
        indicator.setVolumeContraction(new BigDecimal("0.9"));

        BreakoutEvaluation evaluation = evaluate();

        assertEquals(Set.of(BreakoutCriterion.ADR, BreakoutCriterion.VOLUME_CONTRACTION), evaluation.failed());
        assertEquals(5, evaluation.passedCount());
        assertFalse(evaluation.allPassed());
    }

    // ---------- Ingen flagga ----------

    @Test
    void noConsolidation_returnsEmpty() {
        indicator.setConsolidationRange(null);

        assertTrue(evaluator.evaluate(price, indicator).isEmpty());
    }

    @Test
    void noPullback_returnsEmpty() {
        indicator.setPullback(null);

        assertTrue(evaluator.evaluate(price, indicator).isEmpty());
    }

    @Test
    void noVolumeContraction_returnsEmpty() {
        indicator.setVolumeContraction(null);

        assertTrue(evaluator.evaluate(price, indicator).isEmpty());
    }
}