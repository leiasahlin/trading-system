package com.qullamaggie.tradingsystem.alerts.service;

import com.qullamaggie.tradingsystem.alerts.PositionSizeCalculator;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    private final BigDecimal accountSize;
    private final BigDecimal riskPercent;

    public AlertService(ScanResultRepository scanResultRepository,
                        IndicatorRepository indicatorRepository,
                        AlertRepository alertRepository,
                        PositionSizeCalculator positionSizeCalculator,
                        @Value("${trading.account.size}") BigDecimal accountSize,
                        @Value("${trading.account.risk-percent}") BigDecimal riskPercent) {
        this.scanResultRepository = scanResultRepository;
        this.indicatorRepository = indicatorRepository;
        this.alertRepository = alertRepository;
        this.positionSizeCalculator = positionSizeCalculator;
        this.accountSize = accountSize;
        this.riskPercent = riskPercent;
    }

    /**
     * Creates a buy alert from a single scan result.
     * Skips the scan if a pending (NEW) alert already exists for the stock,
     * to avoid duplicate signals. Derives the entry from the consolidation high,
     * the stop from the consolidation low, calculates position size based on
     * account risk, and persists the alert with a human-readable summary.
     *
     * @param scanResult the scan result to turn into an alert
     */

    public void createAlertFromScan(ScanResult scanResult) {
        Stock stock = scanResult.getStock();

        if (alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)) {
            return;
        }

        Optional<Indicator> latestIndicator = indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
        if (latestIndicator.isEmpty()) {
            return;
        }
        Indicator indicator = latestIndicator.get();

        BigDecimal entry = indicator.getConsolidationHigh();
        BigDecimal stop = indicator.getConsolidationLow();
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
