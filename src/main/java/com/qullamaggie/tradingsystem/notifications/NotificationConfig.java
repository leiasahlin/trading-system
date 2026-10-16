package com.qullamaggie.tradingsystem.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where notifications go, and whether they are sent at all. The sending
 * account itself is configured by Spring's own spring.mail.* properties.
 *
 * @param to      recipient address
 * @param enabled false logs notifications instead of sending them
 */
@ConfigurationProperties(prefix = "trading.notifications")
public record NotificationConfig(String to, @DefaultValue("true") boolean enabled) {
    public NotificationConfig {
        if (enabled && (to == null || to.isBlank())) {
            throw new IllegalArgumentException(
                    "trading.notifications.to måste anges när notiser är påslagna");
        }
    }
}