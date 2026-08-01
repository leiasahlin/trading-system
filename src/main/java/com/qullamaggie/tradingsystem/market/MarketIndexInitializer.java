package com.qullamaggie.tradingsystem.market;

import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.entity.StockType;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures the configured market indexes exist as Stock rows so the regular
 * price and indicator pipeline can fetch data for them. Idempotent - runs
 * once at startup and does nothing for indexes that already exist.
 */
@Component
public class MarketIndexInitializer {

    private final StockRepository stockRepository;
    private final MarketRegimeConfig config;

    public MarketIndexInitializer(StockRepository stockRepository, MarketRegimeConfig config) {
        this.stockRepository = stockRepository;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexesExist() {
        for (String symbol : config.indexes()) {
            if (!stockRepository.existsBySymbol(symbol)) {
                Stock index = new Stock();
                index.setSymbol(symbol);
                index.setType(StockType.INDEX);
                stockRepository.save(index);
            }
        }
    }
}
