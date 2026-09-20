package com.qullamaggie.tradingsystem.data.entity;

/**
 * Lifecycle status of an alert.
 * Kept minimal for now — extend with ACTED, EXPIRED etc. when the
 * frontend interaction model is known.
 */

public enum AlertStatus {
    NEW,
    DISMISSED
}
