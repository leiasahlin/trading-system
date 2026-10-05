package com.qullamaggie.tradingsystem.scanner.service;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.scanner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
    private final ParabolicShortConfig parabolicConfig;
    private final ParabolicShortEvaluator parabolicShortEvaluator;
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final BreakoutScanConfig breakoutConfig;
    private final EpisodicPivotScanConfig episodicPivotConfig;

    public ScanService(DailyPriceRepository dailyPriceRepository,
                       IndicatorRepository indicatorRepository,
                       ScanResultRepository scanResultRepository,
                       StockRepository stockRepository, MarketDataProvider marketDataProvider, IndicatorCalculator calculator, ParabolicShortConfig parabolicConfig, ParabolicShortEvaluator parabolicShortEvaluator,
                       BreakoutScanConfig breakoutConfig, EpisodicPivotScanConfig episodicPivotConfig) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.scanResultRepository = scanResultRepository;
        this.marketDataProvider = marketDataProvider;
        this.calculator = calculator;
        this.stockRepository = stockRepository;
        this.parabolicConfig = parabolicConfig;
        this.parabolicShortEvaluator = parabolicShortEvaluator;
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

        if (indicator.getConsolidationRange() == null || indicator.getPullback() == null
                || indicator.getVolumeContraction() == null) {
            return;   // ingen giltig flagga identifierad
        }

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
        for (Stock s : stockRepository.findByEligibleTrue()) {
            try {
                scanStock(s);
            } catch (Exception e) {
                log.warn("Breakout-scan misslyckades för {}: {}", s.getSymbol(), e.getMessage());
            }
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
        // Har aktien redan gjort en stor flermånadersrörelse är gapet ingen överraskning
        if (indicator.getPriorMove() != null
                && indicator.getPriorMove().compareTo(episodicPivotConfig.maxPriorMovePercent()) > 0) {
            return;
        }
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
        for (Stock s : stockRepository.findByEligibleTrue()) {
            try {
                scanStockForEpisodicPivot(s);
            } catch (Exception e) {
                log.warn("EP-scan misslyckades för {}: {}", s.getSymbol(), e.getMessage());
            }
        }
    }

    public void scanStockForParabolicShort(Stock stock) {
        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        if (latestIndicator.isEmpty()) {
            return;
        }
        Indicator indicator = latestIndicator.get();

        List<DailyPrice> prices = dailyPriceRepository.findByStockOrderByDateDesc(stock);
        if (prices.size() < parabolicConfig.lookbackDays()) {
            return;
        }

        List<DailyPrice> window = new ArrayList<>(prices.subList(0, parabolicConfig.lookbackDays()));
        Collections.reverse(window);

        // Marknadsvärde kostar 50 krediter per anrop och används bara här, så det
        // hämtas först när aktiens struktur faktiskt ser parabolisk ut
        if (!parabolicShortEvaluator.hasParabolicStructure(window)) {
            return;
        }
        refreshMarketCapIfStale(stock, LocalDate.now());
        if (stock.getMarketCapUsd() == null) {
            return;
        }

        boolean isParabolic = parabolicShortEvaluator.isParabolicShort(
                window, stock.getMarketCapUsd(), indicator.getEma10(), indicator.getVolumeAvg20());

        if (isParabolic) {
            ScanResult result = new ScanResult();
            result.setStock(stock);
            result.setSetupType(SetupType.PARABOLIC_SHORT);
            result.setAdr(indicator.getAdr20());
            scanResultRepository.save(result);
        }
    }

    public void scanAllStocksForParabolicShort() {
        for (Stock s : stockRepository.findByEligibleTrue()) {
            try {
                scanStockForParabolicShort(s);
            } catch (Exception e) {
                log.warn("Parabolic-scan misslyckades för {}: {}", s.getSymbol(), e.getMessage());
            }
        }
    }

    /**
     * Fetches market cap when it's missing or stale. It only matters for the
     * parabolic setup's extension threshold, and each call is expensive, so it
     * happens here rather than for every stock in the universe filter.
     */
    private void refreshMarketCapIfStale(Stock stock, LocalDate asOfDate) {
        LocalDate lastUpdated = stock.getMarketCapUpdatedAt();
        boolean needsRefresh = lastUpdated == null
                || ChronoUnit.DAYS.between(lastUpdated, asOfDate) >= parabolicConfig.marketCapMaxAgeDays();

        if (!needsRefresh) {
            return;
        }

        BigDecimal marketCap = marketDataProvider.fetchMarketCap(stock.getSymbol());
        if (marketCap == null) {
            return;   // stämpeln sätts inte, så nästa körning försöker igen
        }

        stock.setMarketCapUsd(marketCap);
        stock.setMarketCapUpdatedAt(asOfDate);
        stockRepository.save(stock);
    }
}
