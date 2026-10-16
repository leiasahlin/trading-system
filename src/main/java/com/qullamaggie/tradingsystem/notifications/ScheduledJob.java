package com.qullamaggie.tradingsystem.notifications;

import java.util.List;

/**
 * The scheduled jobs that report failures by email. Each one knows what its
 * failure means for the user and how to run it again by hand.
 *
 * @param displayName  used in the email, e.g. "kvällskörningen avbröts"
 * @param consequence  what the user is missing while the job is broken
 * @param rerunPaths   API paths that redo the job, in order; empty if there's no endpoint
 */
public enum ScheduledJob {
    EVENING_PIPELINE("kvällskörningen",
            "Nya köpsignaler och positionsvarningar för idag kan saknas, och inget kvällsmejl har skickats.",
            List.of("/pipeline/run", "/notifications/evening-digest")),
    EVENING_DIGEST("kvällsmejlet",
            "Kvällens signaler och varningar har inte mejlats. De kommer med i nästa kvällsmejl.",
            List.of("/notifications/evening-digest")),
    EP_SCAN("EP-scanningen",
            "Dagens EP-signaler kan saknas.",
            List.of("/pipeline/episodic-pivot")),
    OVERNIGHT_CHECK("övernattskontrollen",
            "Ingen har kontrollerat om någon position är för stor att behålla över natten.",
            List.of()),
    INTRADAY("bevakningen under dagen",
            "Stoppbrott upptäcks inte medan felet finns kvar. Kolla dina stop loss i Avanza.",
            List.of()),
    DISCOVERY("marknadsgenomsökningen",
            "Inga nya aktier har lagts till i bevakningen den här veckan.",
            List.of("/pipeline/discovery"));

    private final String displayName;
    private final String consequence;
    private final List<String> rerunPaths;

    ScheduledJob(String displayName, String consequence, List<String> rerunPaths) {
        this.displayName = displayName;
        this.consequence = consequence;
        this.rerunPaths = rerunPaths;
    }

    public String displayName() {
        return displayName;
    }

    public String consequence() {
        return consequence;
    }

    public List<String> rerunPaths() {
        return rerunPaths;
    }
}
