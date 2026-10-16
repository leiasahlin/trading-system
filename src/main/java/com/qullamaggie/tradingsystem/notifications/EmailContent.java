package com.qullamaggie.tradingsystem.notifications;

/**
 * A finished email, ready to hand to NotificationService. Sent as both
 * plain text and HTML; the mail client picks the one it can show.
 */
public record EmailContent(String subject, String text, String html) {}
