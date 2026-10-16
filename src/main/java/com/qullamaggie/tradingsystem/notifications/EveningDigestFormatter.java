package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.PositionAlertType;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.notifications.EmailDocument.*;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.qullamaggie.tradingsystem.notifications.Format.*;

/**
 * Turns an EveningDigest into the evening email.
 *
 * The email is built for someone who knows the strategy but not this
 * system's internals: first what to do, in short label/value rows, then
 * how every number was calculated, so nothing has to be taken on trust.
 */
@Component
public class EveningDigestFormatter {

    private static final DateTimeFormatter SUBJECT_DATE = DateTimeFormatter.ofPattern("d/M");
    private static final String API = "http://localhost:8080/api";
    private static final int MAX_SUBJECT_ACTIONS = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final EmailRenderer renderer;

    public EveningDigestFormatter(EmailRenderer renderer) {
        this.renderer = renderer;
    }

    public EmailContent format(EveningDigest digest) {
        return renderer.render(document(digest));
    }

    EmailDocument document(EveningDigest digest) {
        List<Block> actions = new ArrayList<>();
        List<Block> calculations = new ArrayList<>();
        List<Block> commands = new ArrayList<>();
        List<String> subjectParts = new ArrayList<>();

        for (BuySignal signal : digest.buySignals()) {
            subjectParts.add((signal.setup().isShort() ? "blanka " : "köp ") + signal.symbol());
            actions.add(buyItem(signal));
            calculations.add(buyCalculation(signal, digest.account()));
            commands.add(buyCommand(signal));
        }
        for (PositionWarning warning : digest.positionWarnings()) {
            subjectParts.add(subjectLabel(warning));
            actions.add(warningItem(warning));
            calculations.add(warningCalculation(warning, digest.sellRules()));
            Command command = sellCommand(warning);
            if (command != null) {
                commands.add(command);
            }
        }
        for (OvernightWarning warning : digest.overnightWarnings()) {
            subjectParts.add("minska " + warning.name());
            actions.add(overnightItem(warning, digest.account()));
            calculations.add(overnightCalculation(warning, digest.account()));
        }
        calculations.add(marketCalculation(digest));

        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Köp / sälj", actions.isEmpty()
                ? List.<Block>of(new Paragraph("Inget att köpa eller sälja i kväll."))
                : actions));
        sections.add(new Section("Marknaden", List.of(new Paragraph(marketText(digest.regimeStatus())))));
        sections.add(new Section("Så har systemet räknat", calculations));
        if (!commands.isEmpty()) {
            sections.add(new Section("Registrera i systemet", commands));
        }
        return new EmailDocument(subject(digest.date(), subjectParts), sections);
    }

    // --- Ämnesrad ---

    private String subject(LocalDate date, List<String> parts) {
        String day = date.format(SUBJECT_DATE);
        if (parts.isEmpty()) {
            return "⚪ Kväll " + day + ": ingen åtgärd";
        }
        String shown = String.join(", ", parts.subList(0, Math.min(parts.size(), MAX_SUBJECT_ACTIONS)));
        if (parts.size() > MAX_SUBJECT_ACTIONS) {
            shown += " +" + (parts.size() - MAX_SUBJECT_ACTIONS) + " till";
        }
        return "🟢 Kväll " + day + ": " + shown;
    }

    private String subjectLabel(PositionWarning warning) {
        String symbol = warning.symbol();
        return switch (warning.type()) {
            case TRIM -> "sälj av " + symbol;
            case MOVE_STOP_TO_BREAKEVEN -> "flytta stop " + symbol;
            case TRAILING_STOP_UPDATE -> (warning.setup().isShort() ? "sänk stop " : "höj stop ") + symbol;
            case EXIT -> "sälj " + symbol;
            case STOP_BREACH -> "stop nådd " + symbol;
        };
    }

    // --- Köpsignaler ---

    private Item buyItem(BuySignal signal) {
        boolean isShort = signal.setup().isShort();
        BigDecimal riskPerShare = signal.entry().subtract(signal.stop()).abs();
        BigDecimal shares = BigDecimal.valueOf(signal.shares());

        return new Item((isShort ? "Blanka " : "Köp ") + signal.symbol(), setupLabel(signal.setup()), List.of(
                new Row(isShort ? "Blanka" : "Köp", whole(signal.shares()) + " st när kursen går "
                        + (isShort ? "under " : "över ") + price(signal.entry()) + " $"),
                new Row("Stop loss", price(signal.stop()) + " $"),
                new Row(isShort ? "Positionens värde" : "Totalt pris", "ca " + whole(signal.entry().multiply(shares)) + " $"),
                new Row("Högsta möjliga förlust", "ca " + whole(riskPerShare.multiply(shares)) + " $")));
    }

    private Bullets buyCalculation(BuySignal signal, AccountConfig account) {
        BigDecimal riskPerShare = signal.entry().subtract(signal.stop()).abs();
        List<String> lines = new ArrayList<>(entryAndStopExplanation(signal));

        if (signal.adrPercent() != null) {
            BigDecimal distancePercent = riskPerShare.divide(signal.entry(), 6, RoundingMode.HALF_UP).multiply(HUNDRED);
            boolean withinAdr = distancePercent.compareTo(signal.adrPercent()) <= 0;
            lines.add("Avstånd till stop loss: " + price(riskPerShare) + " $ (" + oneDecimal(distancePercent)
                    + " %). Strategin: får inte vara större än aktiens snittrörelse per dag, ADR ("
                    + oneDecimal(signal.adrPercent()) + " %)." + check(withinAdr));
        } else {
            lines.add("Avstånd till stop loss: " + price(riskPerShare) + " $.");
        }

        if (signal.riskPercent() != null) {
            BigDecimal riskAmount = account.size().multiply(signal.riskPercent());
            lines.add("Risk per affär: " + percent(signal.riskPercent()) + " % av kontot = " + whole(riskAmount)
                    + " $. Strategin: " + percent(account.riskPercentOffensive()) + " % när marknaden går uppåt, "
                    + percent(account.riskPercentDefensive()) + " % annars.");

            if (riskPerShare.signum() > 0) {
                BigDecimal byRisk = riskAmount.divide(riskPerShare, 0, RoundingMode.DOWN);
                BigDecimal maxValue = account.size().multiply(account.maxPositionPercent());
                BigDecimal byCap = maxValue.divide(signal.entry(), 0, RoundingMode.DOWN);
                String sizing = "Antal: " + whole(riskAmount) + " / " + price(riskPerShare) + " = " + whole(byRisk) + " st.";
                String maxPercent = percent(account.maxPositionPercent());
                if (byRisk.compareTo(byCap) > 0) {
                    lines.add(sizing + " Men högst " + maxPercent + " % av kontot får ligga i en aktie: "
                            + whole(maxValue) + " / " + price(signal.entry()) + " = " + whole(byCap)
                            + " st. Det lägsta gäller: " + whole(byCap) + " st.");
                } else {
                    lines.add(sizing + " Ryms inom gränsen på " + maxPercent + " % av kontot per aktie ("
                            + whole(byCap) + " st).");
                }
            }
        }

        lines.add("Högsta förlust: " + whole(signal.shares()) + " × " + price(riskPerShare) + " = "
                + whole(riskPerShare.multiply(BigDecimal.valueOf(signal.shares())))
                + " $, så länge aktien inte gapar förbi stoppen.");

        return new Bullets(signal.symbol() + ", " + (signal.setup().isShort() ? "blankning" : "köp"), lines);
    }

    private List<String> entryAndStopExplanation(BuySignal signal) {
        String entry = price(signal.entry()) + " $";
        String stop = price(signal.stop()) + " $";
        return switch (signal.setup()) {
            case BREAKOUT -> List.of(
                    "Köppris " + entry + " = toppen av konsolideringen, vilofasen efter uppgången. "
                            + "Går kursen över den är det en breakout.",
                    "Stop loss " + stop + " = dagens lägsta kurs.");
            case EPISODIC_PIVOT -> List.of(
                    "Köppris " + entry + " = högsta kursen under öppningsintervallet efter dagens gap.",
                    "Stop loss " + stop + " = dagens lägsta kurs hittills.");
            case PARABOLIC, PARABOLIC_SHORT -> List.of(
                    "Blankningspris " + entry + " = nivån där nedgångssignalen kom.",
                    "Stop loss " + stop + " = strax ovanför dagens högsta kurs.");
        };
    }

    private Command buyCommand(BuySignal signal) {
        return new Command(
                (signal.setup().isShort() ? "Blankat " : "Köpt ") + signal.symbol()
                        + "? Ändra antal och pris om det blev annorlunda.",
                "Invoke-RestMethod -Method Post -Uri " + API + "/alerts/" + signal.alertId()
                        + "/execute -ContentType \"application/json\" -Body '{\"shares\": " + signal.shares()
                        + ", \"fillPrice\": " + json(signal.entry()) + "}'");
    }

    // --- Positionsvarningar ---

    private Item warningItem(PositionWarning warning) {
        String symbol = warning.symbol();
        return switch (warning.type()) {
            case TRIM -> trimItem(warning);
            case MOVE_STOP_TO_BREAKEVEN -> new Item("Flytta stop loss på " + symbol, "breakeven", List.of(
                    new Row("Ny stop loss", stopText(warning.suggestedStop()) + " (ditt inköpspris)")));
            case TRAILING_STOP_UPDATE -> new Item(
                    (warning.setup().isShort() ? "Sänk" : "Höj") + " stop loss på " + symbol, "trailing stop", List.of(
                    new Row("Ny stop loss", stopText(warning.suggestedStop()))));
            case EXIT -> new Item(reduceVerb(warning) + " " + symbol, "resten av positionen", List.of(
                    new Row(reduceVerb(warning), allShares(warning.summary())),
                    new Row("Pris", marketPrice(warning.summary()))));
            case STOP_BREACH -> new Item("Stop loss nådd på " + symbol, null, List.of(
                    new Row(reduceVerb(warning), allShares(warning.summary()) + ", om det inte redan skett"),
                    new Row("Stop loss", stopText(warning.currentStop()))));
        };
    }

    private Item trimItem(PositionWarning warning) {
        PositionSummary summary = warning.summary();
        String title = reduceVerb(warning) + " av " + warning.symbol();
        String tag = "trim, ägd " + days(warning.daysHeld());

        if (summary == null) {
            return new Item(title, tag, List.of(
                    new Row(reduceVerb(warning), "1/3–1/2 av positionen"),
                    new Row("Vid försäljning", "flytta stoppen till inköpspriset")));
        }
        return new Item(title, tag, List.of(
                new Row(reduceVerb(warning), trimRange(summary.remainingShares()) + " av dina "
                        + whole(summary.remainingShares()) + " st"),
                new Row("Pris", marketPrice(summary)),
                new Row("Vid försäljning", "flytta stoppen till inköpspriset " + price(summary.averagePrice()) + " $")));
    }

    private Bullets warningCalculation(PositionWarning warning, SellRuleConfig rules) {
        String symbol = warning.symbol();
        String ma = rules.maPeriod() + "-dagarssnittet";
        PositionSummary summary = warning.summary();

        return switch (warning.type()) {
            case TRIM -> new Bullets(symbol + ", sälj av", trimLines(warning, rules));
            case MOVE_STOP_TO_BREAKEVEN -> new Bullets(symbol + ", flytta stop", List.of(
                    "Du har sålt av en del av positionen. Strategin: flytta stoppen till inköpspriset, "
                            + "så kan resten inte gå med förlust."));
            case TRAILING_STOP_UPDATE -> new Bullets(symbol + ", " + (warning.setup().isShort() ? "sänk" : "höj") + " stop",
                    List.of((warning.suggestedStop() == null ? "" : capitalize(ma) + " är nu "
                            + price(warning.suggestedStop()) + " $. ")
                            + "Strategin: efter avsälj följer stoppen " + ma + ", och den flyttas aldrig tillbaka."));
            case EXIT -> {
                List<String> lines = new ArrayList<>();
                if (summary != null && warning.maValue() != null) {
                    lines.add("Stängde " + price(summary.currentPrice()) + " $, under " + ma + " "
                            + price(warning.maValue()) + " $.");
                }
                lines.add("Strategin: sälj resten vid första stängningen under " + ma + ".");
                yield new Bullets(symbol + ", sälj", lines);
            }
            case STOP_BREACH -> new Bullets(symbol + ", stop loss nådd", List.of(
                    "Kursen gick " + (warning.setup().isShort() ? "över" : "under") + " din stop loss "
                            + stopText(warning.currentStop()) + ". Strategin: sälj hela positionen när stoppen nås."));
        };
    }

    private List<String> trimLines(PositionWarning warning, SellRuleConfig rules) {
        List<String> lines = new ArrayList<>();
        long days = warning.daysHeld();
        boolean withinDays = days >= rules.minDaysHeld() && days <= rules.maxDaysHeld();
        lines.add("Du har ägt aktien i " + days(days) + ". Strategin: sälj av efter "
                + rules.minDaysHeld() + "–" + rules.maxDaysHeld() + " dagar." + check(withinDays));

        PositionSummary summary = warning.summary();
        BigDecimal initialRisk = warning.initialRisk();
        if (summary == null || initialRisk == null || initialRisk.signum() <= 0) {
            return lines;
        }

        BigDecimal result = summary.realizedPnL().add(summary.unrealizedPnL());
        boolean enoughGain = summary.currentR().compareTo(rules.minTrimR()) >= 0;
        String direction = warning.setup().isShort() ? "upp" : "ner";
        lines.add((result.signum() >= 0 ? "Du är upp " + whole(result) : "Du är ner " + whole(result.negate()))
                + " $. Vid köpet riskerade du " + whole(initialRisk) + " $ (avståndet " + direction
                + " till stop loss × antal aktier)"
                + (result.signum() > 0 ? ", så vinsten är " + oneDecimal(summary.currentR()) + " gånger risken." : ".")
                + " Strategin: sälj av tidigare om vinsten är minst " + number(rules.minTrimR())
                + " gånger risken." + check(enoughGain));

        int held = summary.remainingShares();
        lines.add("Antal: 1/3 av " + whole(held) + " = " + whole(third(held)) + " st, 1/2 av "
                + whole(held) + " = " + whole(half(held)) + " st.");
        return lines;
    }

    /** A pre-filled command for registering the sale; null if there's no data to fill it with. */
    private Command sellCommand(PositionWarning warning) {
        PositionSummary summary = warning.summary();
        if (summary == null || summary.remainingShares() == 0) {
            return null;
        }
        boolean isTrim = warning.type() == PositionAlertType.TRIM;
        boolean sellsAll = warning.type() == PositionAlertType.EXIT || warning.type() == PositionAlertType.STOP_BREACH;
        if (!isTrim && !sellsAll) {
            return null;
        }
        int shares = isTrim ? third(summary.remainingShares()) : summary.remainingShares();
        String label = isTrim
                ? "Sålt av " + warning.symbol() + "? Ändra antal och pris till det du faktiskt sålde."
                : "Sålt " + warning.symbol() + "? Ändra priset till det du faktiskt fick.";
        // Vid stoppbrott är stoppen den bästa gissningen på säljpriset
        BigDecimal price = warning.type() == PositionAlertType.STOP_BREACH && warning.currentStop() != null
                ? warning.currentStop() : summary.currentPrice();
        // Det som minskar positionen är motsatt sida mot det som öppnade den
        String side = warning.setup().openingSide() == TransactionType.BUY ? "SELL" : "BUY";
        return new Command(label,
                "Invoke-RestMethod -Method Post -Uri " + API + "/positions/" + warning.positionId()
                        + "/transactions -ContentType \"application/json\" -Body '{\"type\": \"" + side
                        + "\", \"shares\": " + shares + ", \"price\": " + json(price) + "}'");
    }

    // --- Övernatt ---

    private Item overnightItem(OvernightWarning warning, AccountConfig account) {
        return new Item("Minska " + warning.name(), "övernattsrisk", List.of(
                new Row("Sälj", whole(warning.sharesToSell()) + " st"),
                new Row("Andel av kontot", oneDecimal(warning.weightPercent()) + " % (max "
                        + percent(account.maxOvernightPositionPercent()) + " %)")));
    }

    private Bullets overnightCalculation(OvernightWarning warning, AccountConfig account) {
        return new Bullets(warning.name() + ", minska", List.of(
                warning.name() + " är " + oneDecimal(warning.weightPercent()) + " % av kontot. Strategin: högst "
                        + percent(account.maxOvernightPositionPercent()) + " % i en position över natten, "
                        + "eftersom ett gap kan hoppa förbi stoppen. Förslaget minskar till "
                        + percent(account.overnightTrimTargetPercent()) + " %."));
    }

    // --- Marknaden ---

    private String marketText(RegimeStatus status) {
        if (status == null) {
            return "Marknadsläget är inte bedömt än.";
        }
        return status == RegimeStatus.RISK_ON
                ? "Marknaden går uppåt. Systemet letar efter nya köplägen."
                : "Marknaden går nedåt. Systemet letar inte efter nya breakouts, men bevakar dina positioner.";
    }

    private Bullets marketCalculation(EveningDigest digest) {
        List<String> lines = new ArrayList<>();
        for (IndexReading reading : digest.indexReadings()) {
            if (reading.fastMa() == null || reading.slowMa() == null) {
                lines.add(reading.symbol() + ": ingen data.");
                continue;
            }
            boolean up = reading.fastMa().compareTo(reading.slowMa()) > 0;
            lines.add(reading.symbol() + ": " + reading.fastPeriod() + "-dagarssnittet " + price(reading.fastMa())
                    + " ligger " + (up ? "över" : "under") + " " + reading.slowPeriod() + "-dagarssnittet "
                    + price(reading.slowMa()) + " → " + (up ? "uppåt." : "nedåt."));
        }
        if (digest.indexReadings().size() > 1) {
            lines.add(digest.regimeMode() == RegimeMode.ANY
                    ? "Strategin: marknaden räknas som uppåt när minst ett index går uppåt."
                    : "Strategin: marknaden räknas som uppåt när alla index går uppåt.");
        } else {
            lines.add("Strategin: marknaden räknas som uppåt när det kortare snittet ligger över det längre.");
        }
        return new Bullets("Marknaden", lines);
    }

    // --- Hjälpmetoder ---

    private static String setupLabel(SetupType setup) {
        return switch (setup) {
            case BREAKOUT -> "breakout";
            case EPISODIC_PIVOT -> "episodic pivot";
            case PARABOLIC -> "parabolic";
            case PARABOLIC_SHORT -> "parabolic short";
        };
    }

    /** "Sälj" for a long, "Köp tillbaka" for a short. */
    private static String reduceVerb(PositionWarning warning) {
        return warning.setup().isShort() ? "Köp tillbaka" : "Sälj";
    }

    private static String allShares(PositionSummary summary) {
        return summary == null ? "hela positionen" : "alla dina " + whole(summary.remainingShares()) + " st";
    }

    private static String marketPrice(PositionSummary summary) {
        return summary == null ? "marknadspris" : "marknadspris (stängde " + price(summary.currentPrice()) + " $)";
    }

    private static String stopText(BigDecimal stop) {
        return stop == null ? "okänd" : price(stop) + " $";
    }

    /** "134–200", or just "1" when a third and a half round to overlapping numbers. */
    private static String trimRange(int held) {
        int third = third(held);
        int half = half(held);
        return half <= third ? whole(third) : whole(third) + "–" + whole(half);
    }

    /** At least a third, rounded up. */
    private static int third(int shares) {
        return Math.ceilDiv(shares, 3);
    }

    /** At most half, rounded down. */
    private static int half(int shares) {
        return shares / 2;
    }

    private static String days(long days) {
        return days + (days == 1 ? " dag" : " dagar");
    }

    private static String check(boolean fulfilled) {
        return fulfilled ? " ✔" : " (inte uppfyllt)";
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
