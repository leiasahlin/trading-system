package com.qullamaggie.tradingsystem.alerts.service;

import com.qullamaggie.tradingsystem.alerts.PositionSizeCalculator;
import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.provider.TwelveDataProvider;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
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

    private final BigDecimal accountSize;
    private final BigDecimal riskPercent;

    public AlertService(ScanResultRepository scanResultRepository,
                        IndicatorRepository indicatorRepository,
                        AlertRepository alertRepository,
                        PositionSizeCalculator positionSizeCalculator, MarketDataProvider marketDataProvider,
                        @Value("${trading.account.size}") BigDecimal accountSize,
                        @Value("${trading.account.risk-percent}") BigDecimal riskPercent) {
        this.scanResultRepository = scanResultRepository;
        this.indicatorRepository = indicatorRepository;
        this.alertRepository = alertRepository;
        this.positionSizeCalculator = positionSizeCalculator;
        this.marketDataProvider = marketDataProvider;
        this.accountSize = accountSize;
        this.riskPercent = riskPercent;
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
        }
    }

    /**
     * Creates an episodic pivot alert for the given stock.
     * <p>
     * NOT YET IMPLEMENTED. Per the methodology, an EP entry is the Opening Range
     * High and the stop is the intraday low — both require intraday/pre-market data
     * that the system does not yet collect. This method is a placeholder to be
     * completed once the intraday data path is built.
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

        BigDecimal riskDistancePercent = entry.subtract(stop).divide(entry, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (riskDistancePercent.compareTo(indicator.getAdr20()) > 0) {
            return;
        }

        int shares = positionSizeCalculator.calculateShares(accountSize, riskPercent, entry, stop);

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

        BigDecimal riskDistancePercent = entry.subtract(stop).divide(entry, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        if (riskDistancePercent.compareTo(indicator.getAdr20()) > 0) {
            return;
        }

        int shares = positionSizeCalculator.calculateShares(accountSize, riskPercent, entry, stop);

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
}
