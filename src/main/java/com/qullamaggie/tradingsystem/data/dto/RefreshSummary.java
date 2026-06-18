package com.qullamaggie.tradingsystem.data.dto;

/**
 * Summary of a market data refresh operation.
 *
 * @param stocksProcessed number of stocks that were checked
 * @param rowsFetched total number of new price rows saved
 */
public record RefreshSummary(int stocksProcessed, int rowsFetched) {
}
