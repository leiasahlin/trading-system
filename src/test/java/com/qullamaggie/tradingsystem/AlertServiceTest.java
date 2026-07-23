package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.PositionSizeCalculator;
import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private ScanResultRepository scanResultRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private AlertRepository alertRepository;
    @Mock private PositionSizeCalculator positionSizeCalculator;
    @Mock private MarketDataProvider marketDataProvider;

    private AlertService alertService;
    private Stock stock;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
                scanResultRepository, indicatorRepository, alertRepository,
                positionSizeCalculator, marketDataProvider,
                new BigDecimal("100000"),  // accountSize
                new BigDecimal("0.01"),    // riskPercent
                new BigDecimal("0.20"));   // maxPositionPercent

        stock = new Stock();
        stock.setSymbol("AAPL");
    }

    @Test
    void shouldCreateBreakoutAlert() {
        when(alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)).thenReturn(false);
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "5")));
        when(positionSizeCalculator.calculateShares(any(), any(), any(), any(), any()))
                .thenReturn(200);

        alertService.createAlertFromScan(scanResult(SetupType.BREAKOUT));

        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void shouldSkipWhenPendingAlertExists() {
        when(alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)).thenReturn(true);

        alertService.createAlertFromScan(scanResult(SetupType.BREAKOUT));

        verify(alertRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenRiskDistanceExceedsAdr() {
        when(alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)).thenReturn(false);
        // entry 310, stop 300 → risk ≈ 3.2%, ADR bara 1% → förkastas
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "1")));

        alertService.createAlertFromScan(scanResult(SetupType.BREAKOUT));

        verify(alertRepository, never()).save(any());
    }

    @Test
    void shouldCreateEpisodicPivotAlertFromSnapshot() {
        when(alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)).thenReturn(false);
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "5")));
        when(marketDataProvider.fetchIntradaySnapshot("AAPL"))
                .thenReturn(new IntradaySnapshot("AAPL", LocalDate.now(),
                        new BigDecimal("336"), new BigDecimal("340"),
                        new BigDecimal("336"), 5_000_000L));
        when(positionSizeCalculator.calculateShares(any(), any(), any(), any(), any()))
                .thenReturn(50);

        alertService.createAlertFromScan(scanResult(SetupType.EPISODIC_PIVOT));

        verify(alertRepository).save(any(Alert.class));
    }

    private ScanResult scanResult(SetupType type) {
        ScanResult r = new ScanResult();
        r.setStock(stock);
        r.setSetupType(type);
        return r;
    }

    private Indicator indicatorWith(String consolidationHigh, String consolidationLow, String adr) {
        Indicator i = new Indicator();
        i.setConsolidationHigh(new BigDecimal(consolidationHigh));
        i.setConsolidationLow(new BigDecimal(consolidationLow));
        i.setAdr20(new BigDecimal(adr));
        return i;
    }
}
