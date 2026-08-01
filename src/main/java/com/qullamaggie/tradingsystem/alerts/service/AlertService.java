package com.qullamaggie.tradingsystem.alerts.service;

import com.qullamaggie.tradingsystem.alerts.PositionSizeCalculator;
import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Turns breakout scan results into actionable alerts:
 * derives entry and stop levels, calculates position size,
 * and persists an Alert for the user to act on.
 */
@Service
public class AlertService {
    private final ScanResultRepository scanResultRepository;
    private final IndicatorRepository indicatorRepository;
    private final AlertRepository alertRepository;
    private final PositionSizeCalculator positionSizeCalculator;
    private final MarketDataProvider marketDataProvider;
    private final DailyPriceRepository dailyPriceRepository;

    private final BigDecimal accountSize;
    private final BigDecimal maxPositionPercent;
    private final MarketRegimeService marketRegimeService;
    private final BigDecimal riskPercentDefensive;
    private final BigDecimal riskPercentOffensive;
    private final int parabolicLookbackDays;
    private final BigDecimal parabolicStopMarginPercent;

    public AlertService(ScanResultRepository scanResultRepository,
                        IndicatorRepository indicatorRepository,
                        AlertRepository alertRepository,
                        PositionSizeCalculator positionSizeCalculator, MarketDataProvider marketDataProvider, DailyPriceRepository dailyPriceRepository,
                        @Value("${trading.account.size}") BigDecimal accountSize,
                        @Value("${trading.account.max-position-percent}") BigDecimal maxPositionPercent,
                        MarketRegimeService marketRegimeService,
                        @Value("${trading.account.risk-percent-defensive}") BigDecimal riskPercentDefensive,
                        @Value("${trading.account.risk-percent-offensive}") BigDecimal riskPercentOffensive,
                        @Value("${trading.scanner.parabolic.lookback-days}") int parabolicLookbackDays,
                        @Value("${trading.scanner.parabolic.stop-margin-percent}") BigDecimal parabolicStopMarginPercent) {
        this.scanResultRepository = scanResultRepository;
        this.indicatorRepository = indicatorRepository;
        this.alertRepository = alertRepository;
        this.positionSizeCalculator = positionSizeCalculator;
        this.marketDataProvider = marketDataProvider;
        this.dailyPriceRepository = dailyPriceRepository;
        this.accountSize = accountSize;
        this.maxPositionPercent = maxPositionPercent;
        this.marketRegimeService = marketRegimeService;
        this.riskPercentDefensive = riskPercentDefensive;
        this.riskPercentOffensive = riskPercentOffensive;
        this.parabolicLookbackDays = parabolicLookbackDays;
        this.parabolicStopMarginPercent = parabolicStopMarginPercent;
    }

    /**
     * Creates an alert from a single scan result.
     * Skips the scan if a pending (NEW) alert already exists for the stock,
     * to avoid duplicate signals. Delegates to the setup-specific alert logic
     * based on the scan result's setup type.
     *
     * @param scanResult the scan result to turn into an alert
     */
    public void createAlertFromScan(ScanResult scanResult) {
        Stock stock = scanResult.getStock();

        // Skip if there already exists a pending alert for the stock
        if (alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)) {
            return;
        }

        // Move to correct logic based on setup type
        switch (scanResult.getSetupType()) {
            case BREAKOUT -> createBreakoutAlert(stock);
            case EPISODIC_PIVOT -> createEpisodicPivotAlert(stock);
            case PARABOLIC_SHORT -> createParabolicShortAlert(stock);
        }
    }

    /**
     * Creates an episodic pivot alert for the given stock.
     *
     * @param stock the stock to create an episodic pivot alert for
     */
    private void createEpisodicPivotAlert(Stock stock) {
        // EP entry = ORH, stop = intraday low, then ADR-validate and size like breakout.
        IntradaySnapshot snapshot = marketDataProvider.fetchIntradaySnapshot(stock.getSymbol());

        if (snapshot == null) {
            return;
        }

        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        if (latestIndicator.isEmpty()) {
            return;
        }
        Indicator indicator = latestIndicator.get();

        BigDecimal entry = snapshot.openingRangeHigh();
        BigDecimal stop = snapshot.intradayLow();

        BigDecimal riskDistancePercent = entry.subtract(stop).abs().divide(entry, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (riskDistancePercent.compareTo(indicator.getAdr20()) > 0) {
            return;
        }

        int shares = positionSizeCalculator.calculateShares(accountSize, currentRiskPercent(), entry,
                stop, maxPositionPercent);

        Alert alert = new Alert();
        alert.setStock(stock);
        alert.setType("EPISODIC_PIVOT_BUY");
        alert.setEntryPrice(entry);
        alert.setStopPrice(stop);
        alert.setShares(shares);
        alert.setPrice(entry);
        alert.setMessage("Episodic pivot buy: " + shares + " shares, entry " + entry + ", stop " + stop);
        alertRepository.save(alert);
    }

    /**
     * Creates a breakout buy alert for the given stock.
     * Entry is the consolidation high (breakout level), stop is the consolidation low.
     * Rejects the trade if the entry-to-stop distance exceeds the stock's ADR
     * (per the methodology's ADR validation rule).
     *
     * @param stock the stock to create a breakout alert for
     */
    public void createBreakoutAlert(Stock stock) {
        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        if (latestIndicator.isEmpty()) {
            return;
        }
        Indicator indicator = latestIndicator.get();

        BigDecimal entry = indicator.getConsolidationHigh();
        BigDecimal stop = indicator.getConsolidationLow();

        BigDecimal riskDistancePercent = entry.subtract(stop).abs().divide(entry, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (riskDistancePercent.compareTo(indicator.getAdr20()) > 0) {
            return;
        }

        int shares = positionSizeCalculator.calculateShares(accountSize, currentRiskPercent(), entry, stop, maxPositionPercent);

        Alert alert = new Alert();
        alert.setStock(stock);
        alert.setType("BREAKOUT_BUY");
        alert.setEntryPrice(entry);
        alert.setStopPrice(stop);
        alert.setShares(shares);
        alert.setPrice(entry);
        alert.setMessage("Breakout buy: " + shares + " shares, entry " + entry + ", stop " + stop);
        alertRepository.save(alert);
    }

    /**
     * Creates alerts for all scan results that don't already have a pending alert.
     */
    public void createAlertsForAllScans() {
        List<ScanResult> scans = scanResultRepository.findAll();
        for (ScanResult scan : scans) {
            createAlertFromScan(scan);
        }
    }

    /**
     * Risk per trade depends on the market regime: the source specifies a
     * defensive default with an offensive setting when conditions are
     * constructive. Falls back to defensive when no regime has been assessed.
     */
    private BigDecimal currentRiskPercent() {
        boolean riskOn = marketRegimeService.getLatest()
                .map(regime -> regime.getStatus() == RegimeStatus.RISK_ON)
                .orElse(false);

        return riskOn ? riskPercentOffensive : riskPercentDefensive;
    }

    /**
     * Creates a parabolic short alert for the given stock.
     * <p>
     * Stop is placed just above the highest high of the run-up, per the source.
     * Entry is approximated with the latest close: the methodology's actual
     * triggers are intraday (a break below the opening range low, or a failed
     * reclaim of VWAP), which requires intraday data the system does not yet
     * collect. Replace determineEntryPrice() once that path exists.
     *
     * @param stock the stock to create a parabolic short alert for
     */
    private void createParabolicShortAlert(Stock stock) {
        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        if (latestIndicator.isEmpty()) {
            return;
        }
        Indicator indicator = latestIndicator.get();

        List<DailyPrice> prices = dailyPriceRepository.findByStockOrderByDateDesc(stock);
        if (prices.size() < parabolicLookbackDays) {
            return;
        }
        List<DailyPrice> window = prices.subList(0, parabolicLookbackDays);

        BigDecimal entry = determineEntryPrice(window);
        BigDecimal stop = determineStopPrice(window);

        if (entry == null || stop == null) {
            return;
        }

        BigDecimal riskDistancePercent = entry.subtract(stop).abs()
                .divide(entry, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (riskDistancePercent.compareTo(indicator.getAdr20()) > 0) {
            return;
        }

        int shares = positionSizeCalculator.calculateShares(
                accountSize, currentRiskPercent(), entry, stop, maxPositionPercent);

        Alert alert = new Alert();
        alert.setStock(stock);
        alert.setType("PARABOLIC_SHORT");
        alert.setEntryPrice(entry);
        alert.setStopPrice(stop);
        alert.setShares(shares);
        alert.setPrice(entry);
        alert.setMessage("Parabolic short: " + shares + " shares, entry " + entry
                + ", stop " + stop + " (entry approximated from daily close)");
        alertRepository.save(alert);
    }

    /**
     * APPROXIMATION: the latest close stands in for the intraday entry trigger.
     * When intraday data is available, this should instead return the opening
     * range low or the VWAP reclaim level.
     *
     * @param window lookback window, newest first
     */
    private BigDecimal determineEntryPrice(List<DailyPrice> window) {
        return window.getFirst().getClose();
    }

    /**
     * Stop sits just above the highest high of the run-up, per the source.
     *
     * @param window lookback window, newest first
     */
    private BigDecimal determineStopPrice(List<DailyPrice> window) {
        BigDecimal highest = window.getFirst().getHigh();
        for (DailyPrice price : window) {
            highest = highest.max(price.getHigh());
        }
        BigDecimal margin = highest
                .multiply(parabolicStopMarginPercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        return highest.add(margin);
    }
}
