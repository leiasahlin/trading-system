package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.PositionSizeCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PositionSizeCalculatorTest {

    private final PositionSizeCalculator calculator = new PositionSizeCalculator();

    @Test
    void shouldCalculateCorrectShareCount() {
        int shares = calculator.calculateShares(
                new BigDecimal("100000"),
                new BigDecimal("0.01"),
                new BigDecimal("100"),
                new BigDecimal("95"));

        assertEquals(200, shares);
    }

    @Test
    void shouldReturnZeroWhenStopEqualsEntry() {
        int shares = calculator.calculateShares(
                new BigDecimal("100000"),
                new BigDecimal("0.01"),
                new BigDecimal("100"),
                new BigDecimal("100"));

        assertEquals(0, shares);
    }
}