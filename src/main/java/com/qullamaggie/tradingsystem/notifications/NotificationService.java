package com.qullamaggie.tradingsystem.notifications;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Sends notification emails. Every subject is prefixed so the mails are
 * easy to filter in the inbox.
 */
@Service
public class NotificationService {

    static final String SUBJECT_PREFIX = "[Qullamaggie] ";
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final NotificationConfig config;

    public NotificationService(JavaMailSender mailSender, NotificationConfig config) {
        this.mailSender = mailSender;
        this.config = config;
    }

    /**
     * Sends a plain-text email.
     *
     * @return true if the mail was sent, false if notifications are disabled
     * @throws org.springframework.mail.MailException if sending fails
     *         (wrong password, no connection) - callers decide how to handle it
     */
    public boolean send(String subject, String body) {
        if (!config.enabled()) {
            log.info("Notiser avstängda, skulle ha skickat: {}", subject);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(config.to());
        message.setSubject(SUBJECT_PREFIX + subject);
        message.setText(body);
        mailSender.send(message);

        log.info("Mejl skickat till {}: {}", config.to(), subject);
        return true;
    }

    /**
     * Sends an email with both an HTML and a plain-text version. The mail
     * client shows the HTML if it can, otherwise the text.
     *
     * @return true if the mail was sent, false if notifications are disabled
     * @throws org.springframework.mail.MailException if building or sending fails
     */
    public boolean send(EmailContent email) {
        if (!config.enabled()) {
            log.info("Notiser avstängda, skulle ha skickat: {}", email.subject());
            return false;
        }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            // true = multipart, behövs för att skicka både text och HTML
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(config.to());
            helper.setSubject(SUBJECT_PREFIX + email.subject());
            helper.setText(email.text(), email.html());
        } catch (MessagingException e) {
            throw new MailPreparationException("Kunde inte bygga mejlet: " + email.subject(), e);
        }
        mailSender.send(message);

        log.info("Mejl skickat till {}: {}", config.to(), email.subject());
        return true;
    }

    public boolean sendTest() {
        return send("Testmejl",
                "Det här är ett testmejl från trading-system, skickat " + LocalDateTime.now() + ".\n\n"
                        + "Kommer det fram är mejlinställningarna rätt.");
    }
}
