package com.qullamaggie.tradingsystem.indicators.service;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class IndicatorService {

    private final DailyPriceRepository dailyPriceRepository;
    private final IndicatorRepository indicatorRepository;
    private final IndicatorCalculator calculator;
    private final StockRepository stockRepository;

    public IndicatorService(DailyPriceRepository dailyPriceRepository,
                            IndicatorRepository indicatorRepository,
                            IndicatorCalculator indicatorCalculator,
                            StockRepository stockRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.calculator = indicatorCalculator;
        this.stockRepository = stockRepository;
    }

    public void calculateAndSaveIndicators(Stock stock) {
        // Fetch all the price history from repository (newest first)
        List<DailyPrice> prices = new ArrayList<>(dailyPriceRepository.findByStockOrderByDateDesc(stock));

        // Validate enough data is used
        if (prices.size() < 50) {
            return;
        }

        // reverse to oldest-first so ADR/ATR can use the previous day's close
        Collections.reverse(prices);

        //Moving averages over the most recent 10,20 and 50 days
        List<BigDecimal> closesMa10 = lastN(prices,10).stream().map(DailyPrice::getClose).toList();
        List<BigDecimal> closesMa20 = lastN(prices, 20).stream().map(DailyPrice::getClose).toList();
        List<BigDecimal> closesMa50 = lastN(prices, 50).stream().map(DailyPrice::getClose).toList();

        BigDecimal ma10 = calculator.calculateMA(closesMa10);
        BigDecimal ma20 = calculator.calculateMA(closesMa20);
        BigDecimal ma50 = calculator.calculateMA(closesMa50);

        // ADR and ATR use 21 days (20 ranges + 1 for the first day's previous close)
        BigDecimal adr20 = calculator.calculateADR(lastN(prices, 21));
        BigDecimal atr20 = calculator.calculateATR(lastN(prices, 21));

        // Average volume over the most recent 20 days
        List<Long> volumes20 = lastN(prices, 20).stream().map(DailyPrice :: getVolume).toList();
        Long volumeAvg20 = calculator.calculateAverageVolume(volumes20);

        // Prior move over 60 trading days (3 months)
        BigDecimal priorMove = calculator.findPriorMove(lastN(prices, 60));

        // Consolidation range over the most recent 10 trading days
        BigDecimal consolidationRange = calculator.calculateConsolidationRange(lastN(prices, 10));

        LocalDate date = prices.getLast().getDate();

        // Reuse the existing indicator row for this date if it exists, otherwise
        // create a new one
        Indicator indicator = indicatorRepository.findByStockAndDate(stock, date)
                .orElseGet(() -> {
                    Indicator newIndicator = new Indicator();
                    newIndicator.setStock(stock);
                    newIndicator.setDate(date);
                    return newIndicator;
                        });

        // Fill in all calculated values and persist
        indicator.setMa10(ma10);
        indicator.setMa20(ma20);
        indicator.setMa50(ma50);
        indicator.setAdr20(adr20);
        indicator.setAtr20(atr20);
        indicator.setVolumeAvg20(volumeAvg20);
        indicator.setPriorMove(priorMove);
        indicator.setConsolidationRange(consolidationRange);
        indicatorRepository.save(indicator);
    }

    public void calculateForAllStocks() {
        List<Stock> stocks = stockRepository.findAll();

        for (Stock stock : stocks) {
            calculateAndSaveIndicators(stock);
        }
    }

    private List<DailyPrice> lastN(List<DailyPrice> prices, int n) {
        if (n >= prices.size()) {
            return prices;
        }
        return prices.subList(prices.size() - n, prices.size());
    }
}
