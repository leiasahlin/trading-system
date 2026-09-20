package com.qullamaggie.tradingsystem.data.dto;

/** Reference data for one listed symbol, from the provider's symbol list. */
public record SymbolInfo(String symbol, String name, String isin, String type, String exchange) {}