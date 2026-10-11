package com.qullamaggie.tradingsystem.portfolio;

public class PositionNotFoundException extends RuntimeException {
    public PositionNotFoundException(Long positionId) {
        super("Position hittades inte: " + positionId);
    }
}
