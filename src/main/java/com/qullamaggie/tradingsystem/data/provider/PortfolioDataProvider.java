package com.qullamaggie.tradingsystem.data.provider;

import com.qullamaggie.tradingsystem.data.dto.PortfolioHolding;
import com.qullamaggie.tradingsystem.data.dto.PortfolioSnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * Provides data about the user's portfolio holdings.
 * The broker is the source for what is currently owned — the system
 * never stores its own copy of holdings, only the strategy context behind them.
 * Abstracted so the broker can be swapped without affecting monitoring logic.
 */
public interface PortfolioDataProvider {

    /** Holdings and account value from ONE login/fetch - use when both are needed. */
    PortfolioSnapshot fetchSnapshot();

    default List<PortfolioHolding> fetchHoldings() {
        return fetchSnapshot().holdings();
    }

    default BigDecimal fetchAccountValue() {
        return fetchSnapshot().accountValue();
    }
}
