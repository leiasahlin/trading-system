package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.notifications.EmailContent;
import com.qullamaggie.tradingsystem.notifications.NotificationConfig;
import com.qullamaggie.tradingsystem.notifications.NotificationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private JavaMailSender mailSender;

    private NotificationService enabled() {
        return new NotificationService(mailSender, new NotificationConfig("leia@example.com", true));
    }

    private NotificationService disabled() {
        return new NotificationService(mailSender, new NotificationConfig("leia@example.com", false));
    }

    // --- Vanlig text ---

    @Test
    void send_buildsMessageWithRecipientPrefixedSubjectAndBody() {
        boolean sent = enabled().send("MSTR breakout", "Entry 171,19");

        assertTrue(sent);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertArrayEquals(new String[]{"leia@example.com"}, message.getTo());
        assertEquals("[Qullamaggie] MSTR breakout", message.getSubject());
        assertEquals("Entry 171,19", message.getText());
    }

    @Test
    void send_whenDisabled_sendsNothing() {
        assertFalse(disabled().send("MSTR breakout", "Entry 171,19"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_whenMailFails_propagatesException() {
        doThrow(new MailSendException("auth failed")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> enabled().send("Test", "Test"));
    }

    // --- HTML + text ---

    @Test
    void sendHtml_buildsMultipartWithPrefixedSubject() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        boolean sent = enabled().send(new EmailContent("Kväll 16/10: köp MSTR", "text", "<p>html</p>"));

        assertTrue(sent);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertEquals("[Qullamaggie] Kväll 16/10: köp MSTR", message.getSubject());
        assertEquals("leia@example.com", message.getAllRecipients()[0].toString());
        assertInstanceOf(MimeMultipart.class, message.getContent());
    }

    @Test
    void sendHtml_whenDisabled_buildsNothing() {
        assertFalse(disabled().send(new EmailContent("Kväll", "text", "<p>html</p>")));

        verify(mailSender, never()).createMimeMessage();
    }

    // --- Inställningar ---

    @Test
    void config_enabledWithoutRecipient_throws() {
        assertThrows(IllegalArgumentException.class, () -> new NotificationConfig("", true));
    }

    @Test
    void config_disabledWithoutRecipient_isAllowed() {
        assertDoesNotThrow(() -> new NotificationConfig(null, false));
    }
}
