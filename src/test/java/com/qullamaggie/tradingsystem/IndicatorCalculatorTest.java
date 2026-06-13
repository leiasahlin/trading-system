package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IndicatorCalculatorTest {
    @Test
    public void calculateMA_withFiveValues_returnsCorrectAverage() {
        IndicatorCalculator calculator = new IndicatorCalculator();

        List<BigDecimal> closes = List.of(
                new BigDecimal("100"),
                new BigDecimal("102"),
                new BigDecimal("101"),
                new BigDecimal("105"),
                new BigDecimal("107")
        );

        BigDecimal result = calculator.calculateMA(closes);

        //103+102+101+105+10+7 / 5
        assertEquals(new BigDecimal("103.0000"), result);
    }

    @Test
    public void calculateMA_withEmptyList_returnsNull() {
        IndicatorCalculator calculator = new IndicatorCalculator();

        BigDecimal result = calculator.calculateMA(List.of());

        assertNull(result);
    }
}
