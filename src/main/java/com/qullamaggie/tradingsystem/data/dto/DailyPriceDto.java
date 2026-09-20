package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPriceDto(LocalDate date, BigDecimal open, BigDecimal high,
                            BigDecimal low, BigDecimal close, Long volume) {
}
