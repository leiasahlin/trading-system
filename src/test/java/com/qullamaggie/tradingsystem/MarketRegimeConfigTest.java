package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketRegimeConfigTest {

    @Test
    void validConfig_isAccepted() {
        assertDoesNotThrow(() ->
                new MarketRegimeConfig(List.of("SPY"), RegimeMode.ANY, 10, 20));
    }

    @Test
    void emptyIndexList_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new MarketRegimeConfig(List.of(), RegimeMode.ANY, 10, 20));
    }

    @Test
    void fastPeriodNotShorterThanSlow_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new MarketRegimeConfig(List.of("SPY"), RegimeMode.ANY, 20, 10));
    }

    @Test
    void unsupportedMaPeriod_throws() {
        // 15 beräknas inte av IndicatorCalculator - skulle tyst ge RISK_OFF varje dag
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new MarketRegimeConfig(List.of("SPY"), RegimeMode.ANY, 15, 20));

        assertTrue(exception.getMessage().contains("15"));
    }
}
