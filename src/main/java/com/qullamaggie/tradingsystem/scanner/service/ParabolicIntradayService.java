package com.qullamaggie.tradingsystem.scanner.service;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.dto.IntradayBar;
import com.qullamaggie.tradingsystem.data.entity.ScanResult;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.scanner.ParabolicIntradayEvaluator;
import com.qullamaggie.tradingsystem.scanner.ParabolicTriggerType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Watches yesterday's parabolic short candidates for an intraday entry trigger.
 * Each trigger type may fire once per stock and day, independently of the other.
 */
@Service
public class ParabolicIntradayService {

    private final ScanResultRepository scanResultRepository;
    private final AlertRepository alertRepository;
    private final MarketDataProvider marketDataProvider;
    private final ParabolicIntradayEvaluator evaluator;
    private final AlertService alertService;

    public ParabolicIntradayService(ScanResultRepository scanResultRepository,
                                    AlertRepository alertRepository,
                                    MarketDataProvider marketDataProvider,
                                    ParabolicIntradayEvaluator evaluator,
                                    AlertService alertService) {
        this.scanResultRepository = scanResultRepository;
        this.alertRepository = alertRepository;
        this.marketDataProvider = marketDataProvider;
        this.evaluator = evaluator;
        this.alertService = alertService;
    }

    public void checkCandidates() {
        // 3 dagar bakåt så fredagens kvällsscan fortfarande gäller på måndagen
        LocalDateTime since = LocalDateTime.now().minusDays(3);
        for (ScanResult scan : scanResultRepository
                .findBySetupTypeAndScannedAtAfter(SetupType.PARABOLIC_SHORT, since)) {
            checkCandidate(scan.getStock());
        }
    }

    private void checkCandidate(Stock stock) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<ParabolicTriggerType> pending = Arrays.stream(ParabolicTriggerType.values())
                .filter(t -> !alertRepository.existsByStockAndTypeAndCreatedAtAfter(
                        stock, t.alertType(), startOfDay))
                .toList();
        if (pending.isEmpty()) {
            return;   // båda triggerarna har redan larmat idag - inga API-anrop
        }

        List<IntradayBar> bars = marketDataProvider.fetchIntradayBars(stock.getSymbol());
        if (bars.isEmpty()) {
            return;
        }

        for (ParabolicTriggerType trigger : pending) {
            BigDecimal entry = switch (trigger) {
                case OPENING_RANGE_LOW_BREAK -> evaluator.hasBrokenBelowOpeningRangeLow(bars)
                        ? evaluator.openingRangeLow(bars) : null;
                case FAILED_VWAP_RECLAIM -> evaluator.hasFailedVwapReclaim(bars)
                        ? bars.getLast().close() : null;
            };
            if (entry != null) {
                alertService.createParabolicShortAlert(stock, trigger, entry);
            }
        }
    }
}