package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PortfolioAlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PositionAlertRepository;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends the evening summary after the pipeline: every alert not yet
 * notified, plus the market regime and the numbers behind each suggestion.
 *
 * It also repeats stop breaches whose position is still open. Those were
 * already mailed when they happened, but an open position past its stop is
 * the most dangerous state the system knows of, so it is shown every evening
 * until the sale is registered.
 *
 * An alert counts as notified only once the email has actually gone out.
 * If sending fails, notifiedAt stays null and the alert is picked up again
 * by the next email - nothing is lost to a network hiccup.
 */
@Service
public class EveningDigestService {

    private final AlertRepository alertRepository;
    private final PositionAlertRepository positionAlertRepository;
    private final PortfolioAlertRepository portfolioAlertRepository;
    private final MarketRegimeService marketRegimeService;
    private final NotificationDataLoader loader;
    private final EveningDigestFormatter formatter;
    private final NotificationService notificationService;

    public EveningDigestService(AlertRepository alertRepository,
                                PositionAlertRepository positionAlertRepository,
                                PortfolioAlertRepository portfolioAlertRepository,
                                MarketRegimeService marketRegimeService,
                                NotificationDataLoader loader,
                                EveningDigestFormatter formatter,
                                NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.positionAlertRepository = positionAlertRepository;
        this.portfolioAlertRepository = portfolioAlertRepository;
        this.marketRegimeService = marketRegimeService;
        this.loader = loader;
        this.formatter = formatter;
        this.notificationService = notificationService;
    }

    /**
     * @return true if the email was sent, false if notifications are disabled
     * @throws org.springframework.mail.MailException if sending fails; nothing
     *         is marked as notified in that case
     */
    public boolean sendEveningDigest() {
        LocalDate today = LocalDate.now();

        List<Alert> pendingAlerts = alertRepository.findByNotifiedAtIsNull();
        List<PositionAlert> pendingPositionAlerts = positionAlertRepository.findByNotifiedAtIsNull();
        List<PortfolioAlert> pendingPortfolioAlerts = portfolioAlertRepository.findByNotifiedAtIsNull();

        // Redan mejlade stoppbrott där positionen fortfarande är öppen. Oskickade
        // stoppbrott kommer med bland de vanliga varningarna nedan istället
        List<UnhandledStopBreach> unhandled = positionAlertRepository
                .findByTypeAndStatus(PositionAlertType.STOP_BREACH, AlertStatus.NEW).stream()
                .filter(alert -> alert.getNotifiedAt() != null)
                .filter(alert -> alert.getPosition().getStatus() == PositionStatus.OPEN)
                .map(alert -> new UnhandledStopBreach(loader.toPositionWarning(alert, today),
                        alert.getCreatedAt() == null ? null : alert.getCreatedAt().toLocalDate()))
                .toList();

        // Alerts som redan hanterats (köpta/avvisade) innan mejlet hann gå visas inte,
        // men markeras ändå som notifierade nedan
        List<BuySignal> buySignals = pendingAlerts.stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .filter(alert -> alert.getEntryPrice() != null && alert.getStopPrice() != null
                        && alert.getShares() != null)
                .map(loader::toBuySignal)
                .toList();

        // Varningar för positioner som hunnit stängas är inte längre relevanta
        List<PositionWarning> positionWarnings = pendingPositionAlerts.stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .filter(alert -> alert.getPosition().getStatus() == PositionStatus.OPEN)
                .map(alert -> loader.toPositionWarning(alert, today))
                .toList();

        List<OvernightWarning> overnightWarnings = pendingPortfolioAlerts.stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .map(loader::toOvernightWarning)
                .toList();

        EveningDigest digest = new EveningDigest(today,
                marketRegimeService.getLatest().map(MarketRegime::getStatus).orElse(null),
                loader.marketRegime().regimeMode(),
                loader.indexReadings(),
                loader.account(),
                loader.sellRules(),
                unhandled, buySignals, positionWarnings, overnightWarnings);

        boolean sent = notificationService.send(formatter.format(digest));

        if (sent) {
            markNotified(pendingAlerts, pendingPositionAlerts, pendingPortfolioAlerts);
        }
        return sent;
    }

    private void markNotified(List<Alert> alerts, List<PositionAlert> positionAlerts,
                              List<PortfolioAlert> portfolioAlerts) {
        LocalDateTime now = LocalDateTime.now();
        alerts.forEach(alert -> alert.setNotifiedAt(now));
        positionAlerts.forEach(alert -> alert.setNotifiedAt(now));
        portfolioAlerts.forEach(alert -> alert.setNotifiedAt(now));
        alertRepository.saveAll(alerts);
        positionAlertRepository.saveAll(positionAlerts);
        portfolioAlertRepository.saveAll(portfolioAlerts);
    }
}
