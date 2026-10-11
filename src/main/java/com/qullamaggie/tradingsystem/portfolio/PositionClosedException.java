package com.qullamaggie.tradingsystem.portfolio;

/**
 * Thrown when a transaction is registered against a position that is no
 * longer open. A separate type rather than a plain IllegalStateException,
 * so the controller can map it to 409 without also catching unrelated
 * state errors (e.g. missing price data) as conflicts.
 */
public class PositionClosedException extends RuntimeException {
    public PositionClosedException(Long positionId) {
        super("Position " + positionId + " är redan stängd");
    }
}
