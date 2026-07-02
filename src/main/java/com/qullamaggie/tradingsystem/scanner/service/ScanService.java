package com.qullamaggie.tradingsystem.scanner.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Scans stocks against the Qullamaggie breakout setup criteria
 * and persists matches as ScanResult rows.
 */
@Service
public class ScanService {
    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorRepository indicatorRepository;
    private final ScanResultRepository scanResultRepository;
    private final StockRepository stockRepository;

    private final BigDecimal minPriorMove;
    private final BigDecimal minAdr;
    private final long minAvgVolume;
    private final BigDecimal maxConsolidationRange;

    public ScanService(DailyPriceRepository dailyPriceRepository,
                       IndicatorRepository indicatorRepository,
                       ScanResultRepository scanResultRepository,
                       StockRepository stockRepository,
                       @Value("${trading.scanner.breakout.min-prior-move}") BigDecimal minPriorMove,
                       @Value("${trading.scanner.breakout.min-adr}") BigDecimal minAdr,
                       @Value("${trading.scanner.breakout.max-consolidation-range}") BigDecimal maxConsolidationRange,
                       @Value("${trading.scanner.breakout.min-avg-volume}") long minAvgVolume) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.scanResultRepository = scanResultRepository;
        this.minPriorMove = minPriorMove;
        this.minAdr = minAdr;
        this.minAvgVolume = minAvgVolume;
        this.stockRepository = stockRepository;
        this.maxConsolidationRange = maxConsolidationRange;
    }

    public void scanStock(Stock stock) {
        // Collect latest indicator and price
        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        Optional<DailyPrice> latestPrice = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);

        if (latestIndicator.isEmpty() || latestPrice.isEmpty()) {
            return;
        }

        Indicator indicator = latestIndicator.get();
        DailyPrice price = latestPrice.get();

        boolean hasPriorMove = indicator.getPriorMove().compareTo(minPriorMove) >= 0;
        boolean hasEnoughVolume = indicator.getVolumeAvg20() >= minAvgVolume;
        boolean hasEnoughAdr = indicator.getAdr20().compareTo(minAdr) >= 0;
        boolean hasMaStack = indicator.getMa10().compareTo(indicator.getMa20()) > 0 && // ma10 > ma20
                indicator.getMa20().compareTo(indicator.getMa50()) > 0 && // ma20 > ma50
                price.getClose().compareTo(indicator.getMa10()) > 0; // close > ma10
        boolean hasTightConsolidation = indicator.getConsolidationRange().compareTo(maxConsolidationRange) <= 0;

        boolean isBreakout = hasPriorMove && hasEnoughVolume && hasEnoughAdr && hasMaStack && hasTightConsolidation;

        if (isBreakout) {
            ScanResult result = new ScanResult();
            result.setStock(stock);
            result.setSetupType(SetupType.BREAKOUT);
            result.setAdr(indicator.getAdr20());
            scanResultRepository.save(result);
        }
    }

    public void scanAllStocks() {
        List<Stock> stocks = stockRepository.findAll();

        for (Stock s : stocks) {
            scanStock(s);
        }
    }
}
