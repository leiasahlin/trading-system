package com.qullamaggie.tradingsystem.data.provider;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
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
}
