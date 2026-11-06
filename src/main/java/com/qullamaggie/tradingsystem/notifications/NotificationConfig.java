package com.qullamaggie.tradingsystem.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Who gets the notifications, and whether they are sent at all. The sending
 * account itself is configured by Spring's own spring.mail.* properties.
 *
 * @param to      recipients; several are written comma-separated in
 *                application.properties and Spring splits them into the list
 * @param enabled false logs notifications instead of sending them
 */
@ConfigurationProperties(prefix = "trading.notifications")
public record NotificationConfig(List<String> to, @DefaultValue("true") boolean enabled) {
    public NotificationConfig {
        // Mellanslag efter kommatecken och tomma poster ska inte bli ogiltiga adresser
        to = to == null ? List.of() : to.stream()
                                      .map(String::trim)
                                      .filter(address -> !address.isEmpty())
                                      .toList();
        if (enabled && to.isEmpty()) {
            throw new IllegalArgumentException(
                    "trading.notifications.to måste anges när notiser är påslagna");
        }
    }

    /** For the mail APIs, which take an array of addresses. */
    public String[] recipients() {
        return to.toArray(String[]::new);
    }

    /** For logs and responses: "a@x.com, b@y.com" */
    public String recipientsText() {
        return String.join(", ", to);
    }
}