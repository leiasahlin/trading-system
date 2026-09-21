package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;
import java.util.List;

/** Holdings and total account value from a single provider fetch. */
public record PortfolioSnapshot(List<PortfolioHolding> holdings, BigDecimal accountValue) {}
