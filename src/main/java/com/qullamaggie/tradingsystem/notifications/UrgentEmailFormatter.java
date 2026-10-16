package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.notifications.EmailDocument.*;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.qullamaggie.tradingsystem.notifications.Format.*;

/**
 * Builds the urgent emails - one per event, sent the moment it's detected.
 * The subject alone says what to do, so it can be acted on from the phone's
 * lock screen. The body reuses the evening email's building blocks, so a
 * stop breach reads the same in both.
 */
@Component
public class UrgentEmailFormatter {

    private final EveningDigestFormatter parts;
    private final EmailRenderer renderer;

    public UrgentEmailFormatter(EveningDigestFormatter parts, EmailRenderer renderer) {
        this.parts = parts;
        this.renderer = renderer;
    }

    /** 🔴 STOP NÅDD: MSTR på 162,90 $ (stop 163,18 $) – sälj 1 100 st */
    public EmailContent stopBreach(PositionWarning warning, SellRuleConfig rules) {
        String level = warning.triggerPrice() != null
                ? "på " + price(warning.triggerPrice()) + " $ (stop " + price(warning.currentStop()) + " $)"
                : (warning.setup().isShort() ? "över " : "under ") + price(warning.currentStop()) + " $";
        String action = warning.summary() == null
                ? "sälj hela positionen"
                : (warning.setup().isShort() ? "köp tillbaka " : "sälj ") + whole(warning.summary().remainingShares()) + " st";
        String subject = "🔴 STOP NÅDD: " + warning.symbol() + " " + level + " – " + action;

        return render(subject, parts.warningItem(warning), parts.warningCalculation(warning, rules),
                parts.sellCommand(warning));
    }

    /** 🔴 ÖVERNATT: minska MicroStrategy med 210 st före stängning */
    public EmailContent overnight(OvernightWarning warning, AccountConfig account) {
        String subject = "🔴 ÖVERNATT: minska " + warning.name() + " med "
                + whole(warning.sharesToSell()) + " st före stängning";
        return render(subject, parts.overnightItem(warning, account), parts.overnightCalculation(warning, account), null);
    }

    /** 🟡 EP-SIGNAL: köp CELH över 48,20 $, stop 45,90 $ */
    public EmailContent epSignal(BuySignal signal, AccountConfig account) {
        String subject = "🟡 EP-SIGNAL: köp " + signal.symbol() + " över " + price(signal.entry())
                + " $, stop " + price(signal.stop()) + " $";
        return render(subject, parts.buyItem(signal), parts.buyCalculation(signal, account), parts.buyCommand(signal));
    }

    private EmailContent render(String subject, Item action, Bullets calculation, Command command) {
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Köp / sälj", List.of(action)));
        sections.add(new Section("Så har systemet räknat", List.of(calculation)));
        if (command != null) {
            sections.add(new Section("Registrera i systemet", List.of(command)));
        }
        return renderer.render(new EmailDocument(subject, sections));
    }
}
