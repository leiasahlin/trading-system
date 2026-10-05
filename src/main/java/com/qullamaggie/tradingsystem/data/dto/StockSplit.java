package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A split event. Prices before the date must be multiplied by ratio and volumes
 * divided by it, since the provider delivers unadjusted history.
 */
public record StockSplit(LocalDate date, BigDecimal ratio) {}
