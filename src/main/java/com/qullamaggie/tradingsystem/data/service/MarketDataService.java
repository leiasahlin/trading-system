package com.qullamaggie.tradingsystem.data.service;

import com.qullamaggie.tradingsystem.data.dto.RefreshSummary;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MarketDataService {
    private final MarketDataProvider marketDataProvider;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;

    public MarketDataService(MarketDataProvider marketDataProvider,
                             StockRepository stockRepository,
                             DailyPriceRepository dailyPriceRepository) {
        this.marketDataProvider = marketDataProvider;
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
    }

    /**
     * Refreshes price data for all tracked stocks.
     * Only fetches missing days since the last stored date.
     */
    public RefreshSummary refreshAllStocks() {
        int sum = 0;
        List<Stock> stocks = stockRepository.findAll();

        for (Stock stock : stocks) {
            sum += refreshPrices(stock);
        }

        return new RefreshSummary(stocks.size(), sum);
    }

    /**
     * Refreshes price data for a single stock.
     * fetches only the days missing sine the last stored date.
     * If no data exists for the stock, fetches one year of history
     * to ensure sufficient data for indicator calculations (MA, ADR, prior move).
     *
     * @param stock the stock to refresh
     */
    public int refreshPrices(Stock stock) {
        Optional<DailyPrice> latestRow = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);

        LocalDate startDate;
        if (latestRow.isEmpty()) {
            // No existing data - fetch one year back to support all indicator calculations
            startDate = LocalDate.now().minusYears(1);
        } else {
            startDate = latestRow.get().getDate().plusDays(1);
        }

        long daysToFetch = ChronoUnit.DAYS.between(startDate, LocalDate.now());

        if (daysToFetch <= 0) {
            return 0;
        }

        List<DailyPrice> prices = marketDataProvider.fetchDailyPrices(stock.getSymbol(), (int) daysToFetch);
        List<DailyPrice> newPrices = new ArrayList<>();

        for (DailyPrice price : prices) {
            if (!price.getDate().isBefore(startDate)) {
                price.setStock(stock);
                newPrices.add(price);
            }
        }
        dailyPriceRepository.saveAll(newPrices);
        return newPrices.size();
    }
}
