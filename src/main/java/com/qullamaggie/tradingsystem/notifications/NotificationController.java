package com.qullamaggie.tradingsystem.notifications;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.*;

/**
 * Lets the mail setup and the evening email be tried without waiting for
 * a real signal or the scheduled pipeline.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final EveningDigestService eveningDigestService;
    private final UrgentNotificationService urgentNotificationService;
    private final NotificationConfig config;

    public NotificationController(NotificationService notificationService,
                                  EveningDigestService eveningDigestService,
                                  UrgentNotificationService urgentNotificationService,
                                  NotificationConfig config) {
        this.notificationService = notificationService;
        this.eveningDigestService = eveningDigestService;
        this.urgentNotificationService = urgentNotificationService;
        this.config = config;
    }

    @PostMapping("/test")
    public ResponseEntity<String> sendTest() {
        return ResponseEntity.ok(result(notificationService.sendTest(), "Testmejl"));
    }

    /**
     * Sends the evening email now. Alerts included are marked as notified,
     * so they won't appear again in the scheduled evening email.
     */
    @PostMapping("/evening-digest")
    public ResponseEntity<String> sendEveningDigest() {
        return ResponseEntity.ok(result(eveningDigestService.sendEveningDigest(), "Kvällsmejl"));
    }

    /**
     * Sends every urgent email that's waiting (stop breaches, overnight
     * warnings, EP signals) - the same thing the scheduled jobs do.
     */
    @PostMapping("/urgent")
    public ResponseEntity<String> sendUrgent() {
        int sent = urgentNotificationService.sendAllPending();
        return ResponseEntity.ok(sent == 0
                ? "Inga brådskande mejl skickades (inget väntar, eller notiser är avstängda)"
                : sent + " brådskande mejl skickade till " + config.to());
    }

    private String result(boolean sent, String what) {
        return sent
                ? what + " skickat till " + config.to()
                : "Notiser är avstängda (trading.notifications.enabled=false)";
    }

    /** Shows why sending failed, e.g. wrong app password, instead of a bare 500. */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<String> handleMailError(MailException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Kunde inte skicka mejl: " + e.getMessage());
    }
}
