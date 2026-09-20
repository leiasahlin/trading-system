package com.qullamaggie.tradingsystem.portfolio;

public class StockAlreadyExistsException extends RuntimeException {
    public StockAlreadyExistsException(String symbol) {
        super("Stock already exists in the system: " + symbol);
    }
}
