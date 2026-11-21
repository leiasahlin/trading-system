package com.qullamaggie.tradingsystem.data.service;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Replaces a stock's whole price history as one unit. Lives in its own bean
 * so that @Transactional applies when MarketDataService calls it.
 */
@Service
public class PriceHistoryWriter {

    private final DailyPriceRepository dailyPriceRepository;

    public PriceHistoryWriter(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
    }

    /**
     * @return number of old rows removed
     */
    @Transactional
    public int replaceHistory(Stock stock, List<DailyPrice> history) {
        int deleted = dailyPriceRepository.deleteByStock(stock);
        dailyPriceRepository.saveAll(history);
        return deleted;
    }
}