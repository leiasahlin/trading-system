package com.qullamaggie.tradingsystem.scanner.service;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.scanner.BreakoutScanConfig;
import com.qullamaggie.tradingsystem.scanner.EpisodicPivotScanConfig;
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
    private final MarketDataProvider marketDataProvider;
    private final IndicatorCalculator calculator;

    private final BreakoutScanConfig breakoutConfig;
    private final EpisodicPivotScanConfig episodicPivotConfig;

    public ScanService(DailyPriceRepository dailyPriceRepository,
                       IndicatorRepository indicatorRepository,
                       ScanResultRepository scanResultRepository,
                       StockRepository stockRepository, MarketDataProvider marketDataProvider, IndicatorCalculator calculator,
                       BreakoutScanConfig breakoutConfig, EpisodicPivotScanConfig episodicPivotConfig) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.scanResultRepository = scanResultRepository;
        this.marketDataProvider = marketDataProvider;
        this.calculator = calculator;
        this.stockRepository = stockRepository;
        this.breakoutConfig = breakoutConfig;
        this.episodicPivotConfig = episodicPivotConfig;

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

        boolean hasPriorMove = indicator.getPriorMove().compareTo(breakoutConfig.minPriorMove()) >= 0 &&
                indicator.getPriorMove().compareTo(breakoutConfig.maxPriorMove()) <= 0;
        boolean hasEnoughVolume = indicator.getVolumeAvg20() >= breakoutConfig.minAvgVolume();
        boolean hasEnoughAdr = indicator.getAdr20().compareTo(breakoutConfig.minAdr()) >= 0;
        boolean closedAboveShortMa = price.getClose().compareTo(indicator.getMa10()) > 0 ||   // close > ma10
                price.getClose().compareTo(indicator.getMa20()) > 0; // or close > ma20
        boolean hasTightConsolidation = indicator.getConsolidationRange().compareTo(breakoutConfig.maxConsolidationRange()) <= 0;
        boolean hasShallowPullback = indicator.getPullback().compareTo(breakoutConfig.maxPullback()) <= 0;
        boolean hasVolumeContraction = indicator.getVolumeContraction().compareTo(breakoutConfig.maxVolumeContraction()) <= 0;

        boolean isBreakout = hasPriorMove && hasEnoughVolume && hasEnoughAdr
                && closedAboveShortMa && hasTightConsolidation && hasShallowPullback && hasVolumeContraction;

        if (isBreakout) {
            ScanResult result = new ScanResult();
            result.setStock(stock);
            result.setSetupType(SetupType.BREAKOUT);
            result.setAdr(indicator.getAdr20());
            scanResultRepository.save(result);
        }
    }

    public void scanAllStocks() {
        List<Stock> stocks = stockRepository.findByEligibleTrue();

        for (Stock s : stocks) {
            scanStock(s);
        }
    }

    public void scanStockForEpisodicPivot(Stock stock) {
        // EP evaluates fresh intraday data: today's open vs yesterday's close (gap),
        // and opening range volume vs the average volume (volume surge).
        Optional<DailyPrice> latestPrice = dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock);
        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        IntradaySnapshot snapshot = marketDataProvider.fetchIntradaySnapshot(stock.getSymbol());

        if (latestIndicator.isEmpty() || snapshot == null || latestPrice.isEmpty()) {
            return;
        }

        Indicator indicator = latestIndicator.get();
        DailyPrice yesterday = latestPrice.get();

        BigDecimal gapPercent = calculator.calculateGap(yesterday.getClose(), snapshot.open());
        BigDecimal relativeVolume = calculator.calculateRelativeVolume(snapshot.openingRangeVolume(),
                indicator.getVolumeAvg50());
        BigDecimal volumeVsYesterday = calculator.calculateRelativeVolume(snapshot.openingRangeVolume(),
                yesterday.getVolume());

        if (gapPercent == null || relativeVolume == null || volumeVsYesterday == null) {
            return;
        }

        boolean hasEnoughGap = gapPercent.compareTo(episodicPivotConfig.minGap()) >= 0;
        boolean hasHighRelativeVolume = relativeVolume.compareTo(episodicPivotConfig.minRelativeVolume()) >= 0;
        boolean hasEnoughVolumeVsYesterday = volumeVsYesterday.compareTo(episodicPivotConfig.minVolumeVsYesterday()) >= 0;

        boolean hasVolumeSurge = hasHighRelativeVolume || hasEnoughVolumeVsYesterday;
        boolean isEpisodicPivot = hasEnoughGap && hasVolumeSurge;

        if (isEpisodicPivot) {
            ScanResult result = new ScanResult();
            result.setStock(stock);
            result.setSetupType(SetupType.EPISODIC_PIVOT);
            result.setAdr(indicator.getAdr20());
            scanResultRepository.save(result);
        }
    }

    public void scanAllStocksForEpisodicPivot() {
        List<Stock> stocks = stockRepository.findByEligibleTrue();

        for (Stock s : stocks) {
            scanStockForEpisodicPivot(s);
        }
    }
}
