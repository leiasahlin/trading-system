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
    private final NotificationConfig config;

    public NotificationController(NotificationService notificationService,
                                  EveningDigestService eveningDigestService,
                                  NotificationConfig config) {
        this.notificationService = notificationService;
        this.eveningDigestService = eveningDigestService;
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