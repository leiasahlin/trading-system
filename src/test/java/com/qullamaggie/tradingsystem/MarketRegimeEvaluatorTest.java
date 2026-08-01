package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.MarketRegimeEvaluator;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketRegimeEvaluatorTest {

    private MarketRegimeEvaluator evaluatorWith(RegimeMode mode) {
        return new MarketRegimeEvaluator(
                new MarketRegimeConfig(List.of("SPY", "QQQ"), mode, 10, 20));
    }

    // --- isConstructive ---

    @Test
    void isConstructive_returnsTrue_whenFastMaAboveSlowMa() {
        assertTrue(evaluatorWith(RegimeMode.ANY)
                .isConstructive(BigDecimal.valueOf(410), BigDecimal.valueOf(400)));
    }

    @Test
    void isConstructive_returnsFalse_whenFastMaBelowSlowMa() {
        assertFalse(evaluatorWith(RegimeMode.ANY)
                .isConstructive(BigDecimal.valueOf(395), BigDecimal.valueOf(400)));
    }

    @Test
    void isConstructive_returnsFalse_whenMasAreEqual() {
        // Strikt "över" - vid exakt lika har ingen korsning skett
        assertFalse(evaluatorWith(RegimeMode.ANY)
                .isConstructive(BigDecimal.valueOf(400), BigDecimal.valueOf(400)));
    }

    @Test
    void isConstructive_returnsFalse_whenDataMissing() {
        MarketRegimeEvaluator evaluator = evaluatorWith(RegimeMode.ANY);

        assertFalse(evaluator.isConstructive(null, BigDecimal.valueOf(400)));
        assertFalse(evaluator.isConstructive(BigDecimal.valueOf(410), null));
    }

    // --- evaluate ---

    @Test
    void evaluate_withAllMode_requiresEveryIndexConstructive() {
        MarketRegimeEvaluator evaluator = evaluatorWith(RegimeMode.ALL);

        assertEquals(RegimeStatus.RISK_ON,
                evaluator.evaluate(Map.of("SPY", true, "QQQ", true)));
        assertEquals(RegimeStatus.RISK_OFF,
                evaluator.evaluate(Map.of("SPY", true, "QQQ", false)));
    }

    @Test
    void evaluate_withAnyMode_acceptsASingleConstructiveIndex() {
        MarketRegimeEvaluator evaluator = evaluatorWith(RegimeMode.ANY);

        assertEquals(RegimeStatus.RISK_ON,
                evaluator.evaluate(Map.of("SPY", true, "QQQ", false)));
        assertEquals(RegimeStatus.RISK_OFF,
                evaluator.evaluate(Map.of("SPY", false, "QQQ", false)));
    }

    @Test
    void evaluate_returnsRiskOff_whenNoVerdicts() {
        assertEquals(RegimeStatus.RISK_OFF, evaluatorWith(RegimeMode.ANY).evaluate(Map.of()));
    }
}
