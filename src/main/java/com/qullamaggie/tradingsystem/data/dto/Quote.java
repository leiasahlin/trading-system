package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;

public record Quote(String symbol, BigDecimal close, long volume) {}