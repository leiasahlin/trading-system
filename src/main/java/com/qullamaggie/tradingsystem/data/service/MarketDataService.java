package com.qullamaggie.tradingsystem.data.service;

import com.qullamaggie.tradingsystem.data.MarketDataConfig;
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
import org.springframework.web.client.HttpClientErrorException;

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
    private static final int MAX_REFRESH_FAILURES = 3;
    private final MarketDataConfig config;


    public MarketDataService(MarketDataProvider marketDataProvider,
                             StockRepository stockRepository,
                             DailyPriceRepository dailyPriceRepository, SplitAdjuster splitAdjuster, MarketDataConfig config) {
        this.marketDataProvider = marketDataProvider;
        this.stockRepository = stockRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.splitAdjuster = splitAdjuster;
        this.config = config;
    }

    /**
     * Refreshes price data for all tracked stocks.
     * Only fetches missing days since the last stored date.
     */
    public RefreshSummary refreshAllStocks() {
        int sum = 0;
        List<Stock> stocks = stockRepository.findByActiveTrue();

        int processed = 0;
        for (Stock stock : stocks) {
            try {
                sum += refreshPrices(stock);
                if (stock.getRefreshFailures() > 0) {
                    stock.setRefreshFailures(0);
                    stockRepository.save(stock);
                }
            } catch (HttpClientErrorException e) {
                if (isUnknownSymbol(e)) {
                    registerRefreshFailure(stock);
                } else {
                    log.warn("Kunde inte uppdatera priser för {}: {}", stock.getSymbol(), e.getMessage());
                }
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
     * Twelve Data answers 400, 404 or 422 when it doesn't recognise the symbol.
     * Other errors (429 rate limit, 5xx) mean the call failed, not that the stock is gone.
     */
    private boolean isUnknownSymbol(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        return status == 400 || status == 404 || status == 422;
    }

    /**
     * Counts consecutive "unknown symbol" answers. After MAX_REFRESH_FAILURES
     * in a row the stock is deactivated so it is no longer fetched or traded.
     */
    private void registerRefreshFailure(Stock stock) {
        int failures = stock.getRefreshFailures() + 1;
        stock.setRefreshFailures(failures);

        if (failures >= MAX_REFRESH_FAILURES) {
            stock.setActive(false);
            stock.setEligible(false);
            log.warn("{} markerad som inaktiv efter {} försök där symbolen inte hittades",
                    stock.getSymbol(), failures);
        } else {
            log.warn("Symbolen {} hittades inte ({} av {})",
                    stock.getSymbol(), failures, MAX_REFRESH_FAILURES);
        }
        stockRepository.save(stock);
    }

    /**
     * Refreshes price data for a single stock.
     * fetches only the days missing sine the last stored date.
     * If no data exists for the stock, fetches history from 2019 and forward.
     * to ensure sufficient data for indicator calculations (MA, ADR, prior move).
     *
     * @param stock the stock to refresh
     */
    public int refreshPrices(Stock stock) {
        Optional<DailyPrice> latestRow = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);

        // New stock: fetch the whole history in one go
        if (latestRow.isEmpty()) {
            List<DailyPrice> history = fetchFullHistory(stock);
            dailyPriceRepository.saveAll(history);
            return history.size();
        }

        // Existing stock: fetch only the days after the last stored date
        LocalDate startDate = latestRow.get().getDate().plusDays(1);

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

        // En split syns som ett hopp mot senast sparade kurs,
        // och då är det den befintliga historiken som behöver justeras
        if (!newPrices.isEmpty()) {
            adjustStoredHistoryIfSplit(stock, latestRow.get(), newPrices);
        }
        return newPrices.size();
    }

    /**
     * Fetches all prices from history-start up to today, split-adjusted and
     * sorted oldest first. Nothing is saved.
     */
    private List<DailyPrice> fetchFullHistory(Stock stock) {
        LocalDate startDate = config.historyStart();
        long daysToFetch = ChronoUnit.DAYS.between(startDate, LocalDate.now());

        List<DailyPrice> prices = marketDataProvider.fetchDailyPrices(stock.getSymbol(), (int) daysToFetch);
        List<DailyPrice> history = new ArrayList<>();

        for (DailyPrice price : prices) {
            if (!price.getDate().isBefore(startDate)) {
                price.setStock(stock);
                history.add(price);
            }
        }

        history.sort(Comparator.comparing(DailyPrice::getDate));
        adjustForSplits(stock, history);
        return history;
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
