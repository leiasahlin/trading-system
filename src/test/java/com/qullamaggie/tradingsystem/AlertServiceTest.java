package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.PositionSizeCalculator;
import com.qullamaggie.tradingsystem.alerts.service.AlertService;
import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Mock private MarketRegimeService marketRegimeService;
    @Mock private DailyPriceRepository dailyPriceRepository;

    private AlertService alertService;
    private Stock stock;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
                scanResultRepository, indicatorRepository, alertRepository,
                positionSizeCalculator, marketDataProvider, dailyPriceRepository,
                new BigDecimal("100000"),      // accountSize
                new BigDecimal("0.20"),        // maxPositionPercent
                marketRegimeService,
                new BigDecimal("0.005"),       // riskPercentDefensive
                new BigDecimal("0.01"),        // riskPercentOffensive
                15,                                 // parabolicLookbackDays
                new BigDecimal("0.5"));        // parabolicStopMarginPercent

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

    private MarketRegime regime(RegimeStatus status) {
        MarketRegime regime = new MarketRegime();
        regime.setStatus(status);
        return regime;
    }

    @Test
    void usesOffensiveRiskPercent_whenRegimeIsRiskOn() {
        when(marketRegimeService.getLatest()).thenReturn(Optional.of(regime(RegimeStatus.RISK_ON)));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "5")));

        alertService.createBreakoutAlert(stock);

        verify(positionSizeCalculator).calculateShares(
                any(), eq(new BigDecimal("0.01")), any(), any(), any());
    }

    @Test
    void usesDefensiveRiskPercent_whenRegimeIsRiskOff() {
        when(marketRegimeService.getLatest()).thenReturn(Optional.of(regime(RegimeStatus.RISK_OFF)));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "5")));

        alertService.createBreakoutAlert(stock);

        verify(positionSizeCalculator).calculateShares(
                any(), eq(new BigDecimal("0.005")), any(), any(), any());
    }

    @Test
    void usesDefensiveRiskPercent_whenNoRegimeAssessedYet() {
        when(marketRegimeService.getLatest()).thenReturn(Optional.empty());
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "5")));

        alertService.createBreakoutAlert(stock);

        verify(positionSizeCalculator).calculateShares(
                any(), eq(new BigDecimal("0.005")), any(), any(), any());
    }

    @Test
    void shouldCreateParabolicShortAlert_withStopAboveTheRunUpHigh() {
        when(alertRepository.existsByStockAndStatus(stock, AlertStatus.NEW)).thenReturn(false);
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorWith("310", "300", "10")));
        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(parabolicPrices());
        when(positionSizeCalculator.calculateShares(any(), any(), any(), any(), any()))
                .thenReturn(30);

        alertService.createAlertFromScan(scanResult(SetupType.PARABOLIC_SHORT));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(captor.capture());

        Alert alert = captor.getValue();
        // Stoppen ska ligga ÖVER entry vid en blankning, till skillnad från långa setuper
        assertTrue(alert.getStopPrice().compareTo(alert.getEntryPrice()) > 0);
    }

    private List<DailyPrice> parabolicPrices() {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            DailyPrice p = new DailyPrice();
            p.setDate(LocalDate.of(2026, 8, 1).minusDays(i));
            p.setClose(new BigDecimal("300"));
            p.setHigh(new BigDecimal("302"));
            p.setLow(new BigDecimal("298"));
            p.setOpen(new BigDecimal("300"));
            p.setVolume(1_000_000L);
            prices.add(p);
        }
        return prices;
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
