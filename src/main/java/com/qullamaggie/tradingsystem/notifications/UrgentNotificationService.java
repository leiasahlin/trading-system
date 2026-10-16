package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PortfolioAlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PositionAlertRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends the urgent emails right after the job that detected the event:
 * stop breaches after the intraday loop, EP signals after the morning scan,
 * overnight warnings after the overnight check.
 *
 * One email per event, each marked as notified as soon as it is sent. If a
 * send fails the exception propagates and the rest stay unsent - they go out
 * with the next attempt, or with the evening email as a safety net.
 */
@Service
public class UrgentNotificationService {

    private final AlertRepository alertRepository;
    private final PositionAlertRepository positionAlertRepository;
    private final PortfolioAlertRepository portfolioAlertRepository;
    private final NotificationDataLoader loader;
    private final UrgentEmailFormatter formatter;
    private final NotificationService notificationService;

    public UrgentNotificationService(AlertRepository alertRepository,
                                     PositionAlertRepository positionAlertRepository,
                                     PortfolioAlertRepository portfolioAlertRepository,
                                     NotificationDataLoader loader,
                                     UrgentEmailFormatter formatter,
                                     NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.positionAlertRepository = positionAlertRepository;
        this.portfolioAlertRepository = portfolioAlertRepository;
        this.loader = loader;
        this.formatter = formatter;
        this.notificationService = notificationService;
    }

    /** @return the number of emails sent */
    public int sendStopBreaches() {
        LocalDate today = LocalDate.now();
        List<PositionAlert> breaches = positionAlertRepository.findByNotifiedAtIsNull().stream()
                .filter(alert -> alert.getType() == PositionAlertType.STOP_BREACH)
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .filter(alert -> alert.getPosition().getStatus() == PositionStatus.OPEN)
                .toList();

        int sent = 0;
        for (PositionAlert alert : breaches) {
            EmailContent email = formatter.stopBreach(loader.toPositionWarning(alert, today), loader.sellRules());
            if (notificationService.send(email)) {
                alert.setNotifiedAt(LocalDateTime.now());
                positionAlertRepository.save(alert);
                sent++;
            }
        }
        return sent;
    }

    /** @return the number of emails sent */
    public int sendOvernightWarnings() {
        int sent = 0;
        for (PortfolioAlert alert : portfolioAlertRepository.findByNotifiedAtIsNull()) {
            if (alert.getStatus() != AlertStatus.NEW) {
                continue;
            }
            EmailContent email = formatter.overnight(loader.toOvernightWarning(alert), loader.account());
            if (notificationService.send(email)) {
                alert.setNotifiedAt(LocalDateTime.now());
                portfolioAlertRepository.save(alert);
                sent++;
            }
        }
        return sent;
    }

    /** @return the number of emails sent */
    public int sendEpSignals() {
        List<Alert> signals = alertRepository.findByNotifiedAtIsNull().stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .filter(alert -> alert.getType().startsWith("EPISODIC_PIVOT"))
                .filter(alert -> alert.getEntryPrice() != null && alert.getStopPrice() != null
                        && alert.getShares() != null)
                .toList();

        int sent = 0;
        for (Alert alert : signals) {
            EmailContent email = formatter.epSignal(loader.toBuySignal(alert), loader.account());
            if (notificationService.send(email)) {
                alert.setNotifiedAt(LocalDateTime.now());
                alertRepository.save(alert);
                sent++;
            }
        }
        return sent;
    }

    /** Everything urgent that's waiting - used by the test endpoint. */
    public int sendAllPending() {
        return sendStopBreaches() + sendOvernightWarnings() + sendEpSignals();
    }
}
