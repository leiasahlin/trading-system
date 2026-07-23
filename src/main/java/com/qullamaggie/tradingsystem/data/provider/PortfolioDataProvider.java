package com.qullamaggie.tradingsystem.data.provider;

import com.qullamaggie.tradingsystem.data.dto.PortfolioHolding;

import java.math.BigDecimal;
import java.util.List;

/**
 * Provides data about the user's portfolio holdings.
 * The broker is the source for what is currently owned — the system
 * never stores its own copy of holdings, only the strategy context behind them.
 * Abstracted so the broker can be swapped without affecting monitoring logic.
 */
public interface PortfolioDataProvider {

    /**
     * Fetches all current holdings in the portfolio.
     *
     * @return list of current holdings, empty if nothing is held
     */
    List<PortfolioHolding> fetchHoldings();

    /**
     * Fetches the total gross value of the account, including cash and holdings.
     * Used for position sizing and exposure checks.
     *
     * @return the account's total value
     */
    public BigDecimal fetchAccountValue();
}
