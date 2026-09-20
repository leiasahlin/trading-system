package com.qullamaggie.tradingsystem.scanner;

public enum ParabolicTriggerType {
    OPENING_RANGE_LOW_BREAK,
    FAILED_VWAP_RECLAIM;

    public String alertType() {
        return "PARABOLIC_SHORT_" + name();
    }
}
