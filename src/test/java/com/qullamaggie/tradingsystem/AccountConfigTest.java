package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountConfigTest {

    @Test
    void validConfig_isAccepted() {
        assertDoesNotThrow(() -> new AccountConfig(
                new BigDecimal("100000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.20")));
    }

    @Test
    void equalRiskLevels_areAccepted() {
        // Samma nivå i båda lägen är en giltig (om än försiktig) inställning
        assertDoesNotThrow(() -> new AccountConfig(
                new BigDecimal("100000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.20")));
    }

    @Test
    void offensiveBelowDefensive_throws() {
        // Omvänd ordning skulle ge högre risk i dåliga marknader än i goda
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig(
                new BigDecimal("100000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.20")));
    }

    @Test
    void trimTargetNotBelowCeiling_throws() {
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig(
                new BigDecimal("100000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.30")));
    }
}