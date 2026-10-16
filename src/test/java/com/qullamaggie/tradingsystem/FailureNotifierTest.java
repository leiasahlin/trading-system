package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.notifications.EmailContent;
import com.qullamaggie.tradingsystem.notifications.EmailRenderer;
import com.qullamaggie.tradingsystem.notifications.FailureNotifier;
import com.qullamaggie.tradingsystem.notifications.NotificationService;
import com.qullamaggie.tradingsystem.notifications.ScheduledJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailureNotifierTest {

    @Mock private NotificationService notificationService;

    private FailureNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new FailureNotifier(notificationService, new EmailRenderer());
    }

    private EmailContent sentEmail() {
        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(notificationService).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void failure_mailsWhatHappenedTheErrorAndHowToRerun() {
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        notifier.notifyFailure(ScheduledJob.EVENING_PIPELINE,
                new IllegalStateException("Ingen prisdata tillgänglig för SPY"));

        EmailContent email = sentEmail();
        assertEquals("⚫ FEL: kvällskörningen avbröts", email.subject());
        assertTrue(email.text().contains("Kvällskörningen avbröts med ett fel "));
        assertTrue(email.text().contains("inget kvällsmejl har skickats."));
        assertTrue(email.text().contains("IllegalStateException: Ingen prisdata tillgänglig för SPY"));
        assertTrue(email.text().contains("Kör om när felet är åtgärdat:\n"
                + "Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/pipeline/run"));
        assertTrue(email.text().contains("Skicka sedan kvällsmejlet:\n"
                + "Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/notifications/evening-digest"));
    }

    @Test
    void intradayFailure_warnsAboutStops_andHasNoRerunCommand() {
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("Databasen svarar inte"));

        EmailContent email = sentEmail();
        assertTrue(email.text().contains("Stoppbrott upptäcks inte medan felet finns kvar. Kolla dina stop loss i Avanza."));
        assertFalse(email.text().contains("Invoke-RestMethod"));
    }

    @Test
    void sameJobTwiceSameDay_mailsOnlyOnce() {
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("1"));
        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("2"));

        verify(notificationService, times(1)).send(any(EmailContent.class));
    }

    @Test
    void differentJobsSameDay_mailsEach() {
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("1"));
        notifier.notifyFailure(ScheduledJob.OVERNIGHT_CHECK, new RuntimeException("2"));

        verify(notificationService, times(2)).send(any(EmailContent.class));
    }

    @Test
    void mailFails_doesNotThrow_andNextFailureTriesAgain() {
        when(notificationService.send(any(EmailContent.class)))
                .thenThrow(new MailSendException("nätet nere"))
                .thenReturn(true);

        assertDoesNotThrow(() -> notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("1")));
        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("2"));

        verify(notificationService, times(2)).send(any(EmailContent.class));
    }

    @Test
    void notificationsDisabled_nextFailureTriesAgain() {
        when(notificationService.send(any(EmailContent.class))).thenReturn(false);

        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("1"));
        notifier.notifyFailure(ScheduledJob.INTRADAY, new RuntimeException("2"));

        verify(notificationService, times(2)).send(any(EmailContent.class));
    }

    @Test
    void errorWithoutMessage_showsClassName_andRootCause() {
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        notifier.notifyFailure(ScheduledJob.EP_SCAN,
                new RuntimeException(new NullPointerException()));

        String text = sentEmail().text();
        assertTrue(text.contains("Grundorsak: NullPointerException"));
    }
}
