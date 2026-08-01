package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.ScanResult;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.ScanResultRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.scanner.BreakoutScanConfig;
import com.qullamaggie.tradingsystem.scanner.EpisodicPivotScanConfig;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortConfig;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortEvaluator;
import com.qullamaggie.tradingsystem.scanner.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private ScanResultRepository scanResultRepository;
    @Mock private StockRepository stockRepository;
    @Mock private MarketDataProvider marketDataProvider;
    @Mock private IndicatorCalculator calculator;
    @Mock private ParabolicShortEvaluator parabolicShortEvaluator;

    private ScanService scanService;
    private Stock stock;

    @BeforeEach
    void setUp() {
        BreakoutScanConfig breakoutConfig = new BreakoutScanConfig(
                new BigDecimal("30"),   // minPriorMove
                new BigDecimal("100"),  // maxPriorMove
                new BigDecimal("3"),    // minAdr
                1_000_000L,             // minAvgVolume
                new BigDecimal("15"),   // maxConsolidationRange
                new BigDecimal("25"),   // maxPullback
                new BigDecimal("0.8")); // maxVolumeContraction

        EpisodicPivotScanConfig episodicPivotConfig = new EpisodicPivotScanConfig(
                new BigDecimal("10"),   // minGap
                new BigDecimal("1"),    // minRelativeVolume
                new BigDecimal("2"));   // minVolumeVsYesterday

        ParabolicShortConfig parabolicConfig = new ParabolicShortConfig(
                BigDecimal.valueOf(5),                  // minDailyGainPercent
                BigDecimal.valueOf(50),                 // minLargeCapMovePercent
                BigDecimal.valueOf(120),                // minMidCapMovePercent
                BigDecimal.valueOf(300),                // minSmallCapMovePercent
                BigDecimal.valueOf(2_000_000_000L),     // smallCapMaxUsd
                BigDecimal.valueOf(10_000_000_000L),    // largeCapMinUsd
                15,                                     // lookbackDays
                3,                                      // minConsecutiveUpDays
                BigDecimal.valueOf(1.5),                // minBreakdownVolumeRatio
                BigDecimal.valueOf(2.0),                // minChurnVolumeRatio
                BigDecimal.valueOf(2),                  // maxChurnPricePercent
                BigDecimal.valueOf(0.5));               // stopMarginPercent


        scanService = new ScanService(
                dailyPriceRepository, indicatorRepository, scanResultRepository,
                stockRepository, marketDataProvider, calculator, parabolicConfig,
                parabolicShortEvaluator, breakoutConfig, episodicPivotConfig);   // minVolumeVsYesterday

        stock = new Stock();
        stock.setSymbol("AAPL");
    }

    // ---------- Breakout ----------

    @Test
    void shouldSaveScanResultWhenAllBreakoutConditionsMet() {
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(passingBreakoutIndicator()));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(priceWithClose("310")));

        scanService.scanStock(stock);

        verify(scanResultRepository).save(any(ScanResult.class));
    }

    @Test
    void shouldNotSaveWhenPriorMoveTooLow() {
        Indicator indicator = passingBreakoutIndicator();
        indicator.setPriorMove(new BigDecimal("20"));   // under 30

        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicator));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(priceWithClose("310")));

        scanService.scanStock(stock);

        verify(scanResultRepository, never()).save(any());
    }

    @Test
    void shouldNotSaveWhenVolumeHasNotContracted() {
        Indicator indicator = passingBreakoutIndicator();
        indicator.setVolumeContraction(new BigDecimal("1.2"));  // över 0.8

        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicator));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(priceWithClose("310")));

        scanService.scanStock(stock);

        verify(scanResultRepository, never()).save(any());
    }

    @Test
    void shouldNotSaveBreakoutWhenIndicatorMissing() {
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.empty());
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(priceWithClose("310")));

        scanService.scanStock(stock);

        verify(scanResultRepository, never()).save(any());
    }

    // ---------- Episodic pivot ----------

    @Test
    void shouldSaveScanResultWhenGapAndVolumeSurgeMet() {
        stubEpisodicPivotData();
        when(calculator.calculateGap(any(), any())).thenReturn(new BigDecimal("12"));
        when(calculator.calculateRelativeVolume(anyLong(), anyLong()))
                .thenReturn(new BigDecimal("1.5"));

        scanService.scanStockForEpisodicPivot(stock);

        verify(scanResultRepository).save(any(ScanResult.class));
    }

    @Test
    void shouldNotSaveEpisodicPivotWhenGapTooSmall() {
        stubEpisodicPivotData();
        when(calculator.calculateGap(any(), any())).thenReturn(new BigDecimal("2"));  // under 10
        when(calculator.calculateRelativeVolume(anyLong(), anyLong()))
                .thenReturn(new BigDecimal("5"));

        scanService.scanStockForEpisodicPivot(stock);

        verify(scanResultRepository, never()).save(any());
    }

    @Test
    void shouldNotSaveEpisodicPivotWhenSnapshotMissing() {
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(priceWithClose("300")));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(passingBreakoutIndicator()));
        when(marketDataProvider.fetchIntradaySnapshot("AAPL")).thenReturn(null);

        scanService.scanStockForEpisodicPivot(stock);

        verify(scanResultRepository, never()).save(any());
    }

    @Test
    void shouldSaveScanResult_whenEvaluatorFindsParabolicShort() {
        stock.setMarketCapUsd(new BigDecimal("50000000000"));

        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorForParabolic()));
        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(pricesNewestFirst(20));
        when(parabolicShortEvaluator.isParabolicShort(any(), any(), any(), any()))
                .thenReturn(true);

        scanService.scanStockForParabolicShort(stock);

        verify(scanResultRepository).save(any(ScanResult.class));
    }

    @Test
    void shouldNotScanForParabolicShort_whenMarketCapUnknown() {
        // marketCapUsd är null - extremitetströskeln kan inte avgöras
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorForParabolic()));

        scanService.scanStockForParabolicShort(stock);

        verifyNoInteractions(parabolicShortEvaluator);
        verify(scanResultRepository, never()).save(any());
    }

    @Test
    void shouldNotScanForParabolicShort_whenTooFewPrices() {
        stock.setMarketCapUsd(new BigDecimal("50000000000"));

        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(indicatorForParabolic()));
        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(pricesNewestFirst(5));   // under lookbackDays (15)

        scanService.scanStockForParabolicShort(stock);

        verifyNoInteractions(parabolicShortEvaluator);
    }

    // ---------- Help methods ----------

    private Indicator indicatorForParabolic() {
        Indicator i = new Indicator();
        i.setEma10(new BigDecimal("300"));
        i.setVolumeAvg20(1_000_000L);
        i.setAdr20(new BigDecimal("5"));
        return i;
    }

    private List<DailyPrice> pricesNewestFirst(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyPrice p = new DailyPrice();
            p.setDate(LocalDate.of(2026, 8, 1).minusDays(i));
            p.setClose(new BigDecimal("300"));
            p.setHigh(new BigDecimal("305"));
            p.setLow(new BigDecimal("295"));
            p.setOpen(new BigDecimal("300"));
            p.setVolume(1_000_000L);
            prices.add(p);
        }
        return prices;
    }

    private void stubEpisodicPivotData() {
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(priceWithClose("300")));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(passingBreakoutIndicator()));
        when(marketDataProvider.fetchIntradaySnapshot("AAPL"))
                .thenReturn(new IntradaySnapshot("AAPL", LocalDate.now(),
                        new BigDecimal("336"), new BigDecimal("340"),
                        new BigDecimal("334"), 5_000_000L));
    }

    /** Indicator where every breakout condition passes. */
    private Indicator passingBreakoutIndicator() {
        Indicator i = new Indicator();
        i.setPriorMove(new BigDecimal("45"));
        i.setAdr20(new BigDecimal("5"));
        i.setVolumeAvg20(2_000_000L);
        i.setVolumeAvg50(2_000_000L);
        i.setMa10(new BigDecimal("300"));
        i.setMa20(new BigDecimal("295"));
        i.setMa50(new BigDecimal("290"));
        i.setConsolidationRange(new BigDecimal("8"));
        i.setPullback(new BigDecimal("5"));
        i.setVolumeContraction(new BigDecimal("0.6"));
        return i;
    }

    private DailyPrice priceWithClose(String close) {
        DailyPrice p = new DailyPrice();
        p.setClose(new BigDecimal(close));
        p.setVolume(3_000_000L);
        return p;
    }
}
