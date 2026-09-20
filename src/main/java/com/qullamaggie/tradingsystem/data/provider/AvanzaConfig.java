package com.qullamaggie.tradingsystem.data.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "avanza")
public record AvanzaConfig(String username, String password, String totpSecret, String accountId) {}
