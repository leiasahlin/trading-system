package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One completed intraday bar (e.g. 5 minutes), New York time. */
public record IntradayBar(
        LocalDateTime time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {}
