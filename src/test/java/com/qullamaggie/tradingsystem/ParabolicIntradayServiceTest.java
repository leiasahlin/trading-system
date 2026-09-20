package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.dto.IntradayBar;
import com.qullamaggie.tradingsystem.data.entity.ScanResult;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.scanner.ParabolicIntradayEvaluator;
import com.qullamaggie.tradingsystem.scanner.ParabolicTriggerType;
import com.qullamaggie.tradingsystem.scanner.service.ParabolicIntradayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParabolicIntradayServiceTest {

    @Mock private ScanResultRepository scanResultRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private MarketDataProvider marketDataProvider;
    @Mock
    private ParabolicIntradayEvaluator evaluator;
    @Mock private AlertService alertService;

    @InjectMocks
    private ParabolicIntradayService service;

    private Stock stock;
    private final List<IntradayBar> bars = List.of(new IntradayBar(
            LocalDateTime.of(2026, 9, 18, 10, 0), new BigDecimal("97"), new BigDecimal("98"),
            new BigDecimal("96"), new BigDecimal("97"), 100));

    @BeforeEach
    void setUp() {
        stock = new Stock();
        stock.setSymbol("AAPL");
        ScanResult scan = new ScanResult();
        scan.setStock(stock);
        scan.setSetupType(SetupType.PARABOLIC_SHORT);
        when(scanResultRepository.findBySetupTypeAndCreatedAtAfter(eq(SetupType.PARABOLIC_SHORT), any()))
                .thenReturn(List.of(scan));
    }

    @Test
    void firesOrlAlert_withOpeningRangeLowAsEntry() {
        when(alertRepository.existsByStockAndTypeAndCreatedAtAfter(eq(stock), any(), any())).thenReturn(false);
        when(marketDataProvider.fetchIntradayBars("AAPL")).thenReturn(bars);
        when(evaluator.hasBrokenBelowOpeningRangeLow(bars)).thenReturn(true);
        when(evaluator.openingRangeLow(bars)).thenReturn(new BigDecimal("98"));
        when(evaluator.hasFailedVwapReclaim(bars)).thenReturn(false);

        service.checkCandidates();

        verify(alertService).createParabolicShortAlert(
                stock, ParabolicTriggerType.OPENING_RANGE_LOW_BREAK, new BigDecimal("98"));
        verify(alertService, never()).createParabolicShortAlert(
                eq(stock), eq(ParabolicTriggerType.FAILED_VWAP_RECLAIM), any());
    }

    @Test
    void firesVwapAlert_withLastCloseAsEntry() {
        when(alertRepository.existsByStockAndTypeAndCreatedAtAfter(eq(stock), any(), any())).thenReturn(false);
        when(marketDataProvider.fetchIntradayBars("AAPL")).thenReturn(bars);
        when(evaluator.hasBrokenBelowOpeningRangeLow(bars)).thenReturn(false);
        when(evaluator.hasFailedVwapReclaim(bars)).thenReturn(true);

        service.checkCandidates();

        verify(alertService).createParabolicShortAlert(
                stock, ParabolicTriggerType.FAILED_VWAP_RECLAIM, new BigDecimal("97"));
    }

    @Test
    void evaluatesOnlyPendingTriggers_whenOneAlreadyFiredToday() {
        when(alertRepository.existsByStockAndTypeAndCreatedAtAfter(
                eq(stock), eq(ParabolicTriggerType.OPENING_RANGE_LOW_BREAK.alertType()), any())).thenReturn(true);
        when(alertRepository.existsByStockAndTypeAndCreatedAtAfter(
                eq(stock), eq(ParabolicTriggerType.FAILED_VWAP_RECLAIM.alertType()), any())).thenReturn(false);
        when(marketDataProvider.fetchIntradayBars("AAPL")).thenReturn(bars);
        when(evaluator.hasFailedVwapReclaim(bars)).thenReturn(false);

        service.checkCandidates();

        verify(evaluator, never()).hasBrokenBelowOpeningRangeLow(any());
        verify(alertService, never()).createParabolicShortAlert(any(), any(), any());
    }

    @Test
    void skipsDataFetch_whenBothTriggersAlreadyFiredToday() {
        when(alertRepository.existsByStockAndTypeAndCreatedAtAfter(eq(stock), any(), any())).thenReturn(true);

        service.checkCandidates();

        verifyNoInteractions(marketDataProvider, evaluator, alertService);
    }
}
