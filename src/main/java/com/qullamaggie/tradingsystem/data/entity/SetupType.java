package com.qullamaggie.tradingsystem.data.entity;

public enum SetupType {
    BREAKOUT,
    EPISODIC_PIVOT,
    PARABOLIC,
    PARABOLIC_SHORT;

    /**
     * The one place that knows which direction a setup trades. Everything
     * else - opening a position, adding, trimming, PnL - asks this instead
     * of checking for PARABOLIC_SHORT itself.
     */
    public boolean isShort() {
        return this == PARABOLIC_SHORT;
    }

    /**
     * The order side that opens or adds to a position of this setup.
     * The opposite side reduces it (trim, exit or cover).
     */
    public TransactionType openingSide() {
        return isShort() ? TransactionType.SELL : TransactionType.BUY;
    }
}
