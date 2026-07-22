package com.qullamaggie.tradingsystem.scanner.service;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
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

    private final BigDecimal minPriorMove;
    private final BigDecimal maxPriorMove;
    private final BigDecimal minAdr;
    private final long minAvgVolume;
    private final BigDecimal maxConsolidationRange;
    private final BigDecimal minGap;
    private final BigDecimal minRelativeVolume;
    private final BigDecimal maxPullback;
    private final BigDecimal maxVolumeContraction;
    private final BigDecimal minVolumeVsYesterday;

    public ScanService(DailyPriceRepository dailyPriceRepository,
                       IndicatorRepository indicatorRepository,
                       ScanResultRepository scanResultRepository,
                       StockRepository stockRepository, MarketDataProvider marketDataProvider, IndicatorCalculator calculator,
                       @Value("${trading.scanner.breakout.min-prior-move}") BigDecimal minPriorMove,
                       @Value("${trading.scanner.breakout.min-adr}") BigDecimal minAdr,
                       @Value("${trading.scanner.breakout.max-consolidation-range}") BigDecimal maxConsolidationRange,
                       @Value("${trading.scanner.breakout.min-avg-volume}") long minAvgVolume,
                       @Value("${trading.scanner.episodic-pivot.min-gap}") BigDecimal minGap,
                       @Value("${trading.scanner.episodic-pivot.min-relative-volume}") BigDecimal minRelativeVolume,
                       @Value("${trading.scanner.breakout.max-prior-move}") BigDecimal maxPriorMove,
                       @Value("${trading.scanner.breakout.max-pullback}") BigDecimal maxPullback,
                       @Value("${trading.scanner.breakout.max-volume-contraction}") BigDecimal maxVolumeContraction,
                       @Value("${trading.scanner.episodic-pivot.min-volume-vs-yesterday}") BigDecimal minVolumeVsYesterday) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.indicatorRepository = indicatorRepository;
        this.scanResultRepository = scanResultRepository;
        this.marketDataProvider = marketDataProvider;
        this.calculator = calculator;
        this.minPriorMove = minPriorMove;
        this.minAdr = minAdr;
        this.minAvgVolume = minAvgVolume;
        this.stockRepository = stockRepository;
        this.maxConsolidationRange = maxConsolidationRange;
        this.minGap = minGap;
        this.minRelativeVolume = minRelativeVolume;
        this.maxPriorMove = maxPriorMove;
        this.maxPullback = maxPullback;
        this.maxVolumeContraction = maxVolumeContraction;
        this.minVolumeVsYesterday = minVolumeVsYesterday;
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

        boolean hasPriorMove = indicator.getPriorMove().compareTo(minPriorMove) >= 0 &&
                indicator.getPriorMove().compareTo(maxPriorMove) <= 0;
        boolean hasEnoughVolume = indicator.getVolumeAvg20() >= minAvgVolume;
        boolean hasEnoughAdr = indicator.getAdr20().compareTo(minAdr) >= 0;
        boolean closedAboveShortMa = price.getClose().compareTo(indicator.getMa10()) > 0 ||   // close > ma10
                price.getClose().compareTo(indicator.getMa20()) > 0; // or close > ma20
        boolean hasTightConsolidation = indicator.getConsolidationRange().compareTo(maxConsolidationRange) <= 0;
        boolean hasShallowPullback = indicator.getPullback().compareTo(maxPullback) <= 0;
        boolean hasVolumeContraction = indicator.getVolumeContraction().compareTo(maxVolumeContraction) <= 0;

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
        List<Stock> stocks = stockRepository.findAll();

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

        boolean hasEnoughGap = gapPercent.compareTo(minGap) >= 0;
        boolean hasHighRelativeVolume = relativeVolume.compareTo(minRelativeVolume) >= 0;
        boolean hasEnoughVolumeVsYesterday = volumeVsYesterday.compareTo(minVolumeVsYesterday) >= 0;

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
        List<Stock> stocks = stockRepository.findAll();

        for (Stock s : stocks) {
            scanStockForEpisodicPivot(s);
        }
    }
}
