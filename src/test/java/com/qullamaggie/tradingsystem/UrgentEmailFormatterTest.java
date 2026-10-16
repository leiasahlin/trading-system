package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.PositionAlertType;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import com.qullamaggie.tradingsystem.notifications.*;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class UrgentEmailFormatterTest {

    private static final AccountConfig ACCOUNT = new AccountConfig(
            new BigDecimal("1000000"), new BigDecimal("0.20"),
            new BigDecimal("0.005"), new BigDecimal("0.01"),
            new BigDecimal("0.30"), new BigDecimal("0.20"));
    private static final SellRuleConfig RULES = new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10);

    private final EmailRenderer renderer = new EmailRenderer();
    private final UrgentEmailFormatter formatter =
            new UrgentEmailFormatter(new EveningDigestFormatter(renderer), renderer);

    private PositionWarning mstrBreach(BigDecimal triggerPrice, PositionSummary summary) {
        return new PositionWarning(1, "MSTR", SetupType.BREAKOUT, PositionAlertType.STOP_BREACH, null,
                new BigDecimal("163.18"), new BigDecimal("9095"), 2, summary, null, triggerPrice);
    }

    private PositionSummary mstrSummary() {
        return new PositionSummary(new BigDecimal("171.45"), new BigDecimal("162.90"), 1100,
                BigDecimal.ZERO, new BigDecimal("-9405"), new BigDecimal("-1.03"));
    }

    @Test
    void stopBreach_subjectSaysPriceStopAndWhatToSell() {
        EmailContent email = formatter.stopBreach(mstrBreach(new BigDecimal("162.90"), mstrSummary()), RULES);

        assertEquals("🔴 STOP NÅDD: MSTR på 162,90 $ (stop 163,18 $) – sälj 1 100 st", email.subject());
        assertTrue(email.text().contains("STOP LOSS NÅDD PÅ MSTR\nSälj: alla dina 1 100 st, om det inte redan skett\n"
                + "Stop loss: 163,18 $\nKurs vid larmet: 162,90 $\n"));
        assertTrue(email.text().contains("Har du en stop loss-order i Avanza"));
        assertTrue(email.text().contains("/api/positions/1/transactions"));
        assertTrue(email.text().contains("\"shares\": 1100, \"price\": 163.18"));
    }

    @Test
    void stopBreach_withoutPriceOrSummary_stillSaysWhatToDo() {
        EmailContent email = formatter.stopBreach(mstrBreach(null, null), RULES);

        assertEquals("🔴 STOP NÅDD: MSTR under 163,18 $ – sälj hela positionen", email.subject());
        assertFalse(email.text().contains("REGISTRERA I SYSTEMET"));
    }

    @Test
    void overnight_subjectSaysHowManyToSell() {
        EmailContent email = formatter.overnight(
                new OvernightWarning("MicroStrategy", new BigDecimal("34.0000"), 210), ACCOUNT);

        assertEquals("🔴 ÖVERNATT: minska MicroStrategy med 210 st före stängning", email.subject());
        assertTrue(email.text().contains("Andel av kontot: 34,0 % (max 30 %)"));
    }

    @Test
    void epSignal_yellowSubject_withLevels_andFullCalculation() {
        BuySignal ep = new BuySignal(7, "CELH", SetupType.EPISODIC_PIVOT, new BigDecimal("48.20"),
                new BigDecimal("45.90"), 4347, new BigDecimal("0.01"), new BigDecimal("8"));

        EmailContent email = formatter.epSignal(ep, ACCOUNT);

        assertEquals("🟡 EP-SIGNAL: köp CELH över 48,20 $, stop 45,90 $", email.subject());
        String text = email.text();
        assertTrue(text.contains("KÖP CELH (episodic pivot)"));
        assertTrue(text.contains("• Köppris 48,20 $ = högsta kursen under öppningsintervallet efter dagens gap."));
        assertTrue(text.contains("/api/alerts/7/execute"));
    }
}
