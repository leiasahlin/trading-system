package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellRuleConfigTest {

    @Test
    void maPeriod10_isAccepted() {
        assertDoesNotThrow(() ->
                new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10));
    }

    @Test
    void maPeriod20_isAccepted() {
        assertDoesNotThrow(() ->
                new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 20));
    }

    @Test
    void invalidMaPeriod_throwsIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 15));

        assertTrue(exception.getMessage().contains("15"));
    }
}
