package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.notifications.EmailDocument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emails when a scheduled job fails, so a broken pipeline is noticed instead
 * of just going quiet.
 *
 * At most one email per job and day: the intraday loop runs every five
 * minutes, and a lasting error would otherwise send around 80 emails.
 * The memory of what was sent lives only as long as the app runs - after
 * a restart the next failure is reported again, which is fine.
 */
@Component
public class FailureNotifier {

    private static final Logger log = LoggerFactory.getLogger(FailureNotifier.class);
    private static final String API = "http://localhost:8080/api";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;
    private final EmailRenderer renderer;
    private final Map<ScheduledJob, LocalDate> lastNotified = new ConcurrentHashMap<>();

    public FailureNotifier(NotificationService notificationService, EmailRenderer renderer) {
        this.notificationService = notificationService;
        this.renderer = renderer;
    }

    /**
     * Never throws: if the email itself fails (often the same network problem
     * that broke the job) it is logged, and the next failure tries again.
     */
    public void notifyFailure(ScheduledJob job, Exception error) {
        LocalDate today = LocalDate.now();
        if (today.equals(lastNotified.get(job))) {
            log.info("Fel i {} redan mejlat idag, skickar inget nytt", job.displayName());
            return;
        }
        try {
            if (notificationService.send(renderer.render(document(job, error)))) {
                lastNotified.put(job, today);
            }
        } catch (RuntimeException mailError) {
            log.error("Kunde inte mejla felet i {}", job.displayName(), mailError);
        }
    }

    EmailDocument document(ScheduledJob job, Exception error) {
        List<Block> errorBlocks = new ArrayList<>();
        errorBlocks.add(new Paragraph(describe(error)));
        Throwable root = rootCause(error);
        if (root != error) {
            errorBlocks.add(new Paragraph("Grundorsak: " + describe(root)));
        }

        List<Block> actions = new ArrayList<>();
        actions.add(new Paragraph("Kolla loggen i IntelliJ för hela felmeddelandet."));
        List<String> paths = job.rerunPaths();
        for (int i = 0; i < paths.size(); i++) {
            String label = i == 0 ? "Kör om när felet är åtgärdat:" : "Skicka sedan kvällsmejlet:";
            actions.add(new Command(label, "Invoke-RestMethod -Method Post -Uri " + API + paths.get(i)));
        }

        String name = job.displayName();
        return new EmailDocument("⚫ FEL: " + name + " avbröts", List.of(
                new Section("Vad hände", List.of(new Paragraph(
                        Character.toUpperCase(name.charAt(0)) + name.substring(1) + " avbröts med ett fel "
                                + LocalTime.now().format(TIME) + ". " + job.consequence()))),
                new Section("Felet", errorBlocks),
                new Section("Vad du kan göra", actions)));
    }

    /** "IllegalStateException: Ingen prisdata", or just the class name if there's no message. */
    private static String describe(Throwable error) {
        String name = error.getClass().getSimpleName();
        return error.getMessage() == null ? name : name + ": " + error.getMessage();
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
