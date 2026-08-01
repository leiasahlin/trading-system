package com.qullamaggie.tradingsystem.data.provider;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contract for any market data source (e.g. Twelve Data, IBKR, Avanza).
 * Implementations fetch raw OHCLV price history for a given stock symbol,
 * allowing the data source to be swapped without changing the rest of the system.
 */
public interface MarketDataProvider {

    /**
     * Fetches daily OHLCV price history for the given stock symbol
     *
     * @param symbol the ticker symbol (e.g. "AAPL")
     * @param days how many days of history to fetch
     * @return a list of DailyPrice objects (without stock reference set)
     */
    List<DailyPrice> fetchDailyPrices(String symbol, int days);

    /**
     * Fetches the current intraday snapshot for a stock, including opening range
     * high, intraday low, and opening range volume. Used for episodic pivot
     * detection, which needs fresh intraday/pre-market data rather than
     * completed daily bars.
     *
     * @param symbol the ticker symbol
     * @return the current intraday snapshot
     */
    IntradaySnapshot fetchIntradaySnapshot(String symbol);

    /**
     * Fetches the company's current market capitalization in USD.
     * Requires a paid Twelve Data tier - fundamentals are not included
     * in the free plan.
     */
    BigDecimal fetchMarketCap(String symbol);
}
