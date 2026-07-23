package com.qullamaggie.tradingsystem.data.dto;

import java.math.BigDecimal;

/**
 * A single holding in the user's portfolio, as reported by the broker.
 * Represents what is currently owned — quantity and cost basis
 * come from the broker, not from the system's own records.
 *
 * @param symbol       ticker symbol of the held stock
 * @param shares       number of shares currently held
 * @param averagePrice average purchase price per share
 * @param currentPrice current market price per share
 * @param marketValue  current total value of the holding
 */
public record PortfolioHolding(
        String symbol,
        int shares,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal marketValue
) {}
