package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.PositionAlertType;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.notifications.EmailContent;
import com.qullamaggie.tradingsystem.notifications.EmailRenderer;
import com.qullamaggie.tradingsystem.notifications.EveningDigest;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import com.qullamaggie.tradingsystem.notifications.EveningDigestFormatter;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the exact lines of the evening email. The numbers follow the
 * worked example in the design: 1 168 MSTR at 171,19 with stop 163,18 on a
 * 1 000 000 $ account, and 400 NVDA bought at 120 now at 138,40.
 */
class EveningDigestFormatterTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 16);
    private static final AccountConfig ACCOUNT = new AccountConfig(
            new BigDecimal("1000000"), new BigDecimal("0.20"),
            new BigDecimal("0.005"), new BigDecimal("0.01"),
            new BigDecimal("0.30"), new BigDecimal("0.20"));
    private static final SellRuleConfig RULES = new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10);
    private static final IndexReading SPY = new IndexReading("SPY",
            10, new BigDecimal("768.4780"), 20, new BigDecimal("765.6395"));

    private final EveningDigestFormatter formatter = new EveningDigestFormatter(new EmailRenderer());

    // --- Testdata ---

    private EveningDigest digest(List<BuySignal> buys, List<PositionWarning> warnings,
                                 List<OvernightWarning> overnight) {
        return new EveningDigest(DATE, RegimeStatus.RISK_ON, RegimeMode.ALL, List.of(SPY), ACCOUNT, RULES,
                buys, warnings, overnight);
    }

    private EmailContent format(BuySignal... buys) {
        return formatter.format(digest(List.of(buys), List.of(), List.of()));
    }

    private EmailContent format(PositionWarning... warnings) {
        return formatter.format(digest(List.of(), List.of(warnings), List.of()));
    }

    private BuySignal mstr() {
        return new BuySignal(12, "MSTR", SetupType.BREAKOUT, new BigDecimal("171.19"), new BigDecimal("163.18"),
                1168, new BigDecimal("0.0100"), new BigDecimal("6.1"));
    }

    /** 400 NVDA bought at 120, now 138,40: up 7 360 $ on 2 000 $ risk = 3,68R. */
    private PositionSummary nvdaSummary() {
        return new PositionSummary(new BigDecimal("120.00"), new BigDecimal("138.40"), 400,
                BigDecimal.ZERO, new BigDecimal("7360.00"), new BigDecimal("3.68"));
    }

    private PositionWarning nvda(PositionAlertType type, long daysHeld, PositionSummary summary,
                                 BigDecimal suggestedStop) {
        return new PositionWarning(3, "NVDA", SetupType.BREAKOUT, type, suggestedStop, new BigDecimal("115.50"),
                new BigDecimal("2000"), daysHeld, summary, new BigDecimal("131.20"));
    }

    // --- Ämnesrad ---

    @Test
    void nothingToDo_whiteSubject_andSaysSo() {
        EmailContent email = formatter.format(digest(List.of(), List.of(), List.of()));

        assertEquals("⚪ Kväll 16/10: ingen åtgärd", email.subject());
        assertTrue(email.text().contains("Inget att köpa eller sälja i kväll."));
        assertFalse(email.text().contains("REGISTRERA I SYSTEMET"));
    }

    @Test
    void actions_greenSubjectNamesThem() {
        EmailContent email = formatter.format(digest(List.of(mstr()),
                List.of(nvda(PositionAlertType.TRIM, 4, nvdaSummary(), null)), List.of()));

        assertEquals("🟢 Kväll 16/10: köp MSTR, sälj av NVDA", email.subject());
    }

    @Test
    void moreThanThreeActions_subjectIsShortened() {
        EmailContent email = format(mstr(), mstr(), mstr(), mstr(), mstr());

        assertEquals("🟢 Kväll 16/10: köp MSTR, köp MSTR, köp MSTR +2 till", email.subject());
    }

    // --- Köpsignal ---

    @Test
    void buy_showsWhatToDo() {
        String text = format(mstr()).text();

        assertTrue(text.contains("KÖP MSTR (breakout)\n"
                + "Köp: 1 168 st när kursen går över 171,19 $\n"
                + "Stop loss: 163,18 $\n"
                + "Totalt pris: ca 199 950 $\n"
                + "Högsta möjliga förlust: ca 9 356 $\n"));
    }

    @Test
    void buy_explainsEveryNumber() {
        String text = format(mstr()).text();

        assertTrue(text.contains("• Köppris 171,19 $ = toppen av konsolideringen"));
        assertTrue(text.contains("• Stop loss 163,18 $ = dagens lägsta kurs."));
        // 8,01 / 171,19 = 4,68 %
        assertTrue(text.contains("• Avstånd till stop loss: 8,01 $ (4,7 %). Strategin: får inte vara större "
                + "än aktiens snittrörelse per dag, ADR (6,1 %). ✔"));
        assertTrue(text.contains("• Risk per affär: 1 % av kontot = 10 000 $. "
                + "Strategin: 1 % när marknaden går uppåt, 0,5 % annars."));
        // 10 000 / 8,01 = 1 248 men 200 000 / 171,19 = 1 168
        assertTrue(text.contains("• Antal: 10 000 / 8,01 = 1 248 st. Men högst 20 % av kontot får ligga i en "
                + "aktie: 200 000 / 171,19 = 1 168 st. Det lägsta gäller: 1 168 st."));
        assertTrue(text.contains("• Högsta förlust: 1 168 × 8,01 = 9 356 $, så länge aktien inte gapar förbi stoppen."));
    }

    @Test
    void buy_withinPositionCap_saysItFits() {
        BuySignal defensive = new BuySignal(12, "MSTR", SetupType.BREAKOUT, new BigDecimal("171.19"),
                new BigDecimal("163.18"), 624, new BigDecimal("0.005"), new BigDecimal("6.1"));

        String text = format(defensive).text();

        assertTrue(text.contains("• Antal: 5 000 / 8,01 = 624 st. Ryms inom gränsen på 20 % av kontot per aktie (1 168 st)."));
    }

    @Test
    void buy_stopWiderThanAdr_isMarkedNotFulfilled() {
        BuySignal wide = new BuySignal(12, "MSTR", SetupType.BREAKOUT, new BigDecimal("171.19"),
                new BigDecimal("163.18"), 1168, new BigDecimal("0.01"), new BigDecimal("3"));

        assertTrue(format(wide).text().contains("ADR (3,0 %). (inte uppfyllt)"));
    }

    @Test
    void buy_withoutAdrOrRisk_skipsThoseLines() {
        BuySignal old = new BuySignal(12, "MSTR", SetupType.BREAKOUT, new BigDecimal("171.19"),
                new BigDecimal("163.18"), 1168, null, null);

        String text = format(old).text();

        assertTrue(text.contains("• Avstånd till stop loss: 8,01 $.\n"));
        assertFalse(text.contains("Risk per affär"));
    }

    @Test
    void episodicPivot_explainsOpeningRange() {
        BuySignal ep = new BuySignal(7, "CELH", SetupType.EPISODIC_PIVOT, new BigDecimal("48.20"),
                new BigDecimal("45.90"), 100, new BigDecimal("0.01"), new BigDecimal("8"));

        String text = format(ep).text();

        assertTrue(text.contains("KÖP CELH (episodic pivot)"));
        assertTrue(text.contains("• Köppris 48,20 $ = högsta kursen under öppningsintervallet efter dagens gap."));
    }

    @Test
    void buy_commandIsPrefilled() {
        String text = format(mstr()).text();

        assertTrue(text.contains("Köpt MSTR? Ändra antal och pris om det blev annorlunda.\n"
                + "Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/alerts/12/execute "
                + "-ContentType \"application/json\" -Body '{\"shares\": 1168, \"fillPrice\": 171.19}'"));
    }

    // --- Trim ---

    @Test
    void trim_showsWhatToDo() {
        String text = format(nvda(PositionAlertType.TRIM, 4, nvdaSummary(), null)).text();

        assertTrue(text.contains("SÄLJ AV NVDA (trim, ägd 4 dagar)\n"
                + "Sälj: 134–200 av dina 400 st\n"
                + "Pris: marknadspris (stängde 138,40 $)\n"
                + "Vid försäljning: flytta stoppen till inköpspriset 120,00 $\n"));
    }

    @Test
    void trim_explainsWhichRulesAreFulfilled() {
        String text = format(nvda(PositionAlertType.TRIM, 4, nvdaSummary(), null)).text();

        assertTrue(text.contains("• Du har ägt aktien i 4 dagar. Strategin: sälj av efter 3–5 dagar. ✔"));
        assertTrue(text.contains("• Du är upp 7 360 $. Vid köpet riskerade du 2 000 $ (avståndet ner till stop loss "
                + "× antal aktier), så vinsten är 3,7 gånger risken. Strategin: sälj av tidigare om vinsten är "
                + "minst 2 gånger risken. ✔"));
        assertTrue(text.contains("• Antal: 1/3 av 400 = 134 st, 1/2 av 400 = 200 st."));
    }

    @Test
    void trim_beforeDayWindow_daysRuleNotFulfilled() {
        String text = format(nvda(PositionAlertType.TRIM, 2, nvdaSummary(), null)).text();

        assertTrue(text.contains("• Du har ägt aktien i 2 dagar. Strategin: sälj av efter 3–5 dagar. (inte uppfyllt)"));
    }

    @Test
    void trim_atLoss_saysDown_withoutTimesRisk() {
        PositionSummary losing = new PositionSummary(new BigDecimal("120"), new BigDecimal("118.75"), 400,
                BigDecimal.ZERO, new BigDecimal("-500"), new BigDecimal("-0.25"));

        String text = format(nvda(PositionAlertType.TRIM, 4, losing, null)).text();

        assertTrue(text.contains("• Du är ner 500 $. Vid köpet riskerade du 2 000 $ (avståndet ner till stop loss "
                + "× antal aktier). Strategin: sälj av tidigare om vinsten är minst 2 gånger risken. (inte uppfyllt)"));
    }

    @Test
    void trim_withoutPriceData_fallsBackToFractions() {
        String text = format(nvda(PositionAlertType.TRIM, 4, null, null)).text();

        assertTrue(text.contains("Sälj: 1/3–1/2 av positionen\n"));
        assertFalse(text.contains("/api/positions/"));
    }

    @Test
    void trim_commandSellsAThirdAtLastClose() {
        String text = format(nvda(PositionAlertType.TRIM, 4, nvdaSummary(), null)).text();

        assertTrue(text.contains("Sålt av NVDA? Ändra antal och pris till det du faktiskt sålde.\n"
                + "Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/positions/3/transactions "
                + "-ContentType \"application/json\" -Body '{\"type\": \"SELL\", \"shares\": 134, \"price\": 138.40}'"));
    }

    // --- Övriga positionsvarningar ---

    @Test
    void breakeven_showsNewStop() {
        String text = format(nvda(PositionAlertType.MOVE_STOP_TO_BREAKEVEN, 6, nvdaSummary(),
                new BigDecimal("120.00"))).text();

        assertTrue(text.contains("FLYTTA STOP LOSS PÅ NVDA (breakeven)\nNy stop loss: 120,00 $ (ditt inköpspris)\n"));
        assertTrue(text.contains("• Du har sålt av en del av positionen."));
    }

    @Test
    void trailingStop_showsNewStopAndMa() {
        String text = format(nvda(PositionAlertType.TRAILING_STOP_UPDATE, 12, nvdaSummary(),
                new BigDecimal("131.20"))).text();

        assertTrue(text.contains("HÖJ STOP LOSS PÅ NVDA (trailing stop)\nNy stop loss: 131,20 $\n"));
        assertTrue(text.contains("• 10-dagarssnittet är nu 131,20 $. Strategin: efter avsälj följer stoppen "
                + "10-dagarssnittet, och den flyttas aldrig tillbaka."));
    }

    @Test
    void exit_sellsTheRest_andShowsCloseVersusMa() {
        PositionSummary rest = new PositionSummary(new BigDecimal("120"), new BigDecimal("129.80"), 200,
                new BigDecimal("3680"), new BigDecimal("1960"), new BigDecimal("2.82"));

        String text = format(nvda(PositionAlertType.EXIT, 15, rest, null)).text();

        assertTrue(text.contains("SÄLJ NVDA (resten av positionen)\nSälj: alla dina 200 st\n"
                + "Pris: marknadspris (stängde 129,80 $)\n"));
        assertTrue(text.contains("• Stängde 129,80 $, under 10-dagarssnittet 131,20 $."));
        assertTrue(text.contains("\"shares\": 200, \"price\": 129.80"));
    }

    @Test
    void stopBreach_sellsEverything_commandUsesStopAsPrice() {
        String text = format(nvda(PositionAlertType.STOP_BREACH, 2, nvdaSummary(), null)).text();

        assertTrue(text.contains("STOP LOSS NÅDD PÅ NVDA\nSälj: alla dina 400 st, om det inte redan skett\n"
                + "Stop loss: 115,50 $\n"));
        assertTrue(text.contains("• Kursen gick under din stop loss 115,50 $."));
        assertTrue(text.contains("\"shares\": 400, \"price\": 115.50"));
    }

    // --- Övernatt ---

    @Test
    void overnight_showsSharesAndWeight() {
        EmailContent email = formatter.format(digest(List.of(), List.of(),
                List.of(new OvernightWarning("MicroStrategy", new BigDecimal("34.0000"), 210))));

        assertEquals("🟢 Kväll 16/10: minska MicroStrategy", email.subject());
        assertTrue(email.text().contains("MINSKA MICROSTRATEGY (övernattsrisk)\nSälj: 210 st\n"
                + "Andel av kontot: 34,0 % (max 30 %)\n"));
        assertTrue(email.text().contains("Förslaget minskar till 20 %."));
    }

    // --- Marknaden ---

    @Test
    void riskOn_plainLanguage_andIndexExplanation() {
        String text = formatter.format(digest(List.of(), List.of(), List.of())).text();

        assertTrue(text.contains("Marknaden går uppåt. Systemet letar efter nya köplägen."));
        assertTrue(text.contains("• SPY: 10-dagarssnittet 768,48 ligger över 20-dagarssnittet 765,64 → uppåt."));
        assertTrue(text.contains("• Strategin: marknaden räknas som uppåt när det kortare snittet ligger över det längre."));
        assertFalse(text.contains("RISK_ON"));
    }

    @Test
    void riskOff_saysNoNewBreakouts() {
        EveningDigest riskOff = new EveningDigest(DATE, RegimeStatus.RISK_OFF, RegimeMode.ALL, List.of(SPY),
                ACCOUNT, RULES, List.of(), List.of(), List.of());

        assertTrue(formatter.format(riskOff).text()
                .contains("Marknaden går nedåt. Systemet letar inte efter nya breakouts, men bevakar dina positioner."));
    }

    @Test
    void noRegime_saysNotAssessed_andMissingDataShown() {
        EveningDigest none = new EveningDigest(DATE, null, RegimeMode.ALL,
                List.of(new IndexReading("SPY", 10, null, 20, null)), ACCOUNT, RULES, List.of(), List.of(), List.of());

        String text = formatter.format(none).text();

        assertTrue(text.contains("Marknadsläget är inte bedömt än."));
        assertTrue(text.contains("• SPY: ingen data."));
    }

    @Test
    void severalIndexes_explainsRegimeMode() {
        IndexReading qqq = new IndexReading("QQQ", 10, new BigDecimal("500"), 20, new BigDecimal("510"));
        EveningDigest two = new EveningDigest(DATE, RegimeStatus.RISK_OFF, RegimeMode.ALL, List.of(SPY, qqq),
                ACCOUNT, RULES, List.of(), List.of(), List.of());

        String text = formatter.format(two).text();

        assertTrue(text.contains("• QQQ: 10-dagarssnittet 500,00 ligger under 20-dagarssnittet 510,00 → nedåt."));
        assertTrue(text.contains("• Strategin: marknaden räknas som uppåt när alla index går uppåt."));
    }

    // --- HTML ---

    @Test
    void html_hasSameContent() {
        String html = format(mstr()).html();

        assertTrue(html.contains("<b>Köp MSTR</b> – breakout"));
        assertTrue(html.contains(">Högsta möjliga förlust</td>"));
        assertTrue(html.contains("Så har systemet räknat"));
    }
}
