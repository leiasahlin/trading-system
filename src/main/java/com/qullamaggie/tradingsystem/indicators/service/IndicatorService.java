package com.qullamaggie.tradingsystem.indicators.service;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.ConsolidationResult;
import com.qullamaggie.tradingsystem.indicators.FlagPhases;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.indicators.PhaseDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final PhaseDetector phaseDetector;
    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);


    public IndicatorService(DailyPriceRepository dailyPriceRepository,
                            IndicatorRepository indicatorRepository,
                            IndicatorCalculator indicatorCalculator,
                            StockRepository stockRepository, PhaseDetector phaseDetector) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.calculator = indicatorCalculator;
        this.stockRepository = stockRepository;
        this.phaseDetector = phaseDetector;
    }

    public void calculateAndSaveIndicators(Stock stock) {
        // Fetch all the price history from repository (newest first)
        List<DailyPrice> prices = new ArrayList<>(dailyPriceRepository.findByStockOrderByDateDesc(stock));

        // Validate enough data is used
        if (prices.size() < 70) {
            return;
        }

        // reverse to oldest-first so ADR/ATR can use the previous day's close
        Collections.reverse(prices);

        // EMA uses the full history: it's seeded with an SMA over the first `period`
        // closes and then applied forward, so a longer series converges better
        List<BigDecimal> allCloses = prices.stream().map(DailyPrice::getClose).toList();
        BigDecimal ema10 = calculator.calculateEMA(allCloses, 10);

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

        // Average volume over the most recent 50 days
        List<Long> volumes50 = lastN(prices, 50).stream().map(DailyPrice::getVolume).toList();
        Long volumeAvg50 = calculator.calculateAverageVolume(volumes50);

        // Prior move over 60 trading days (3 months)
        BigDecimal priorMove = calculator.findPriorMove(lastN(prices, 60));

        // Gap and relative volume (EP indicators) — based on the most recent day
        DailyPrice today = prices.getLast();
        DailyPrice yesterday = prices.get(prices.size() - 2);

        BigDecimal gapPercent = calculator.calculateGap(yesterday.getClose(), today.getOpen());
        BigDecimal relativeVolume = calculator.calculateRelativeVolume(today.getVolume(), volumeAvg20);

        // Flaggans längd upptäcks per aktie i stället för att antas
        FlagPhases phases = phaseDetector.detect(prices);

        ConsolidationResult consolidation = null;
        BigDecimal pullback = null;
        BigDecimal volumeContraction = null;

        if (phases != null) {
            consolidation = calculator.calculateConsolidation(phases.flag());
            pullback = calculator.calculatePullback(phases.flag());
            volumeContraction = calculator.calculateVolumeContraction(phases.flag(), phases.flagpole());
        }

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
        indicator.setEma10(ema10);
        indicator.setAdr20(adr20);
        indicator.setAtr20(atr20);
        indicator.setVolumeAvg20(volumeAvg20);
        indicator.setVolumeAvg50(volumeAvg50);
        indicator.setPriorMove(priorMove);
        indicator.setConsolidationRange(consolidation == null ? null : consolidation.range());
        indicator.setConsolidationHigh(consolidation == null ? null : consolidation.high());
        indicator.setConsolidationLow(consolidation == null ? null : consolidation.low());
        indicator.setGapPercent(gapPercent);
        indicator.setRelativeVolume(relativeVolume);
        indicator.setPullback(pullback);
        indicator.setVolumeContraction(volumeContraction);
        indicatorRepository.save(indicator);
    }

    public void calculateForAllStocks() {
        for (Stock stock : stockRepository.findAll()) {
            try {
                calculateAndSaveIndicators(stock);
            } catch (Exception e) {
                log.warn("Kunde inte beräkna indikatorer för {}: {}", stock.getSymbol(), e.getMessage());
            }
        }
    }

    private List<DailyPrice> lastN(List<DailyPrice> prices, int n) {
        if (n >= prices.size()) {
            return prices;
        }
        return prices.subList(prices.size() - n, prices.size());
    }

    private List<DailyPrice> allExceptLastN(List<DailyPrice> prices, int n) {
        if (n >= prices.size()) {
            return List.of();
        }
        return prices.subList(0, prices.size() - n);
    }
}
