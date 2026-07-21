package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IntradaySnapshot(
    String symbol,
    LocalDate date,
    BigDecimal open,             // The days open
    BigDecimal openingRangeHigh, // ORH
    BigDecimal intradayLow,      // The days lowest yet (stop)
    Long openingRangeVolume){}
