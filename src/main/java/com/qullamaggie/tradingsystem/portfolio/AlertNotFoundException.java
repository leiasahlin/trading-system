package com.qullamaggie.tradingsystem.portfolio;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(Long alertId) {
        super("Alert hittades inte: " + alertId);
    }
}
