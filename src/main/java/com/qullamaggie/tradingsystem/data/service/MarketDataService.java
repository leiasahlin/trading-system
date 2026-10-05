package com.qullamaggie.tradingsystem.data.service;

import com.qullamaggie.tradingsystem.data.dto.RefreshSummary;
import com.qullamaggie.tradingsystem.data.dto.StockSplit;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class MarketDataService {
    private final MarketDataProvider marketDataProvider;
    private final StockRepository stockRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private final SplitAdjuster splitAdjuster;

    public MarketDataService(MarketDataProvider marketDataProvider,
                             StockRepository stockRepository,
                             DailyPriceRepository dailyPriceRepository, SplitAdjuster splitAdjuster) {
        this.marketDataProvider = marketDataProvider;
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.splitAdjuster = splitAdjuster;
    }

    /**
     * Refreshes price data for all tracked stocks.
     * Only fetches missing days since the last stored date.
     */
    public RefreshSummary refreshAllStocks() {
        int sum = 0;
        List<Stock> stocks = stockRepository.findAll();

        int processed = 0;
        for (Stock stock : stocks) {
            try {
                sum += refreshPrices(stock);
            } catch (Exception e) {
                log.warn("Kunde inte uppdatera priser för {}: {}", stock.getSymbol(), e.getMessage());
            }
            if (++processed % 50 == 0) {
                log.info("Prisuppdatering: {}/{}", processed, stocks.size());
            }
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

        // Första hämtningen: hela historiken kommer ojusterad, så den rättas före save
        if (latestRow.isEmpty()) {
            newPrices.sort(Comparator.comparing(DailyPrice::getDate));
            adjustForSplits(stock, newPrices);
        }

        dailyPriceRepository.saveAll(newPrices);

        // Löpande hämtning: en split syns som ett hopp mot senast sparade kurs,
        // och då är det den befintliga historiken som behöver justeras
        if (latestRow.isPresent() && !newPrices.isEmpty()) {
            adjustStoredHistoryIfSplit(stock, latestRow.get(), newPrices);
        }
        return newPrices.size();
    }

    private void adjustForSplits(Stock stock, List<DailyPrice> prices) {
        if (!splitAdjuster.looksLikeSplit(prices)) {
            return;
        }
        List<StockSplit> splits = marketDataProvider.fetchSplits(stock.getSymbol());
        if (!splits.isEmpty()) {
            log.info("{}: justerar historik för {} split(ar)", stock.getSymbol(), splits.size());
            splitAdjuster.adjust(prices, splits);
        }
    }

    /**
     * A split between runs shows up as a jump from the last stored close to the
     * first new one. The stored history then sits on the old scale and is rewritten.
     */
    private void adjustStoredHistoryIfSplit(Stock stock, DailyPrice lastStored, List<DailyPrice> newPrices) {
        newPrices.sort(Comparator.comparing(DailyPrice::getDate));
        List<DailyPrice> bridge = List.of(lastStored, newPrices.getFirst());
        if (!splitAdjuster.looksLikeSplit(bridge)) {
            return;
        }

        List<StockSplit> splits = marketDataProvider.fetchSplits(stock.getSymbol());
        List<StockSplit> recent = splits.stream()
                .filter(s -> s.date().isAfter(lastStored.getDate()))
                .toList();
        if (recent.isEmpty()) {
            return;
        }

        List<DailyPrice> stored = dailyPriceRepository.findByStockOrderByDateAsc(stock);
        log.info("{}: justerar lagrad historik för {} split(ar)", stock.getSymbol(), recent.size());
        splitAdjuster.adjust(stored, recent);
        dailyPriceRepository.saveAll(stored);
    }
}
