package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.indicators.PhaseDetectionConfig;
import com.qullamaggie.tradingsystem.indicators.PhaseDetector;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.scanner.EpisodicPivotScanConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IndicatorServiceTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;
    @Mock
    private IndicatorRepository indicatorRepository;
    @Mock
    private IndicatorCalculator calculator;
    @Mock
    private StockRepository stockRepository;

    private IndicatorService indicatorService;

    private Stock stock;

    @BeforeEach
    public void setUp() {
        indicatorService = new IndicatorService(
                dailyPriceRepository,
                indicatorRepository,
                calculator,
                stockRepository,
                new PhaseDetector(new PhaseDetectionConfig(10, 40, 60)),
                new EpisodicPivotScanConfig(new BigDecimal("10"), new BigDecimal("1"),
                        new BigDecimal("2"), new BigDecimal("50"), 126, new BigDecimal("0.5")));

        stock = new Stock();
        stock.setSymbol("AAPL");
    }

    @Test
    public void shouldNotSaveWhenTooFewPrices() {
        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(buildPrices(10));

        indicatorService.calculateAndSaveIndicators(stock);

        verify(indicatorRepository, never()).save(any());
    }

    @Test
    public void shouldSaveIndicatorWhenEnoughData() {
        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(buildPrices(80));
        when(indicatorRepository.findByStockAndDate(eq(stock), any()))
                .thenReturn(Optional.empty());

        indicatorService.calculateAndSaveIndicators(stock);

        verify(indicatorRepository).save(any(Indicator.class));
    }

    @Test
    public void shouldReuseExistingIndicatorRow() {
        Indicator existing = new Indicator();
        existing.setId(42L);
        existing.setStock(stock);

        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(buildPrices(80));
        when(indicatorRepository.findByStockAndDate(eq(stock), any()))
                .thenReturn(Optional.of(existing));

        indicatorService.calculateAndSaveIndicators(stock);

        // A new object is saved, but with the existing row's id, so save() updates
        ArgumentCaptor<Indicator> saved = ArgumentCaptor.forClass(Indicator.class);
        verify(indicatorRepository).save(saved.capture());
        assertEquals(42L, saved.getValue().getId());
    }

    @Test
    public void shouldSaveNewRowWithoutIdWhenNoneExists() {
        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(buildPrices(80));
        when(indicatorRepository.findByStockAndDate(eq(stock), any()))
                .thenReturn(Optional.empty());

        indicatorService.calculateAndSaveIndicators(stock);

        ArgumentCaptor<Indicator> saved = ArgumentCaptor.forClass(Indicator.class);
        verify(indicatorRepository).save(saved.capture());
        assertNull(saved.getValue().getId());
        assertEquals(stock, saved.getValue().getStock());
    }

    @Test
    public void shouldLookUpExistingRowByNewestPriceDate() {
        List<DailyPrice> newestFirst = buildPrices(80);
        LocalDate newestDate = newestFirst.getFirst().getDate();

        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(newestFirst);
        when(indicatorRepository.findByStockAndDate(eq(stock), any()))
                .thenReturn(Optional.empty());

        indicatorService.calculateAndSaveIndicators(stock);

        verify(indicatorRepository).findByStockAndDate(stock, newestDate);
    }

    @Test
    public void calculateIndicatorsShouldSetDateToLastPriceInList() {
        List<DailyPrice> oldestFirst = buildPrices(80).reversed();

        Indicator result = indicatorService.calculateIndicators(oldestFirst);

        assertEquals(oldestFirst.getLast().getDate(), result.getDate());
    }

    @Test
    public void calculateIndicatorsShouldOnlyUseDataUpToLastPrice() {
        // The backtest passes a sublist ending at a historical day. The result
        // must belong to that day, not to the newest day in the full history.
        List<DailyPrice> oldestFirst = buildPrices(80).reversed();
        List<DailyPrice> upToDay70 = oldestFirst.subList(0, 70);

        Indicator result = indicatorService.calculateIndicators(upToDay70);

        assertEquals(oldestFirst.get(69).getDate(), result.getDate());
    }

    @Test
    public void calculateIndicatorsShouldNotTouchRepositories() {
        List<DailyPrice> oldestFirst = buildPrices(80).reversed();

        Indicator result = indicatorService.calculateIndicators(oldestFirst);

        assertNull(result.getId());
        assertNull(result.getStock());
        verifyNoInteractions(indicatorRepository, dailyPriceRepository, stockRepository);
    }

    /**
     * Builds a list of dummy DailyPrice objects (newest first),
     * matching the order findByStockOrderByDateDesc returns.
     */
    private List<DailyPrice> buildPrices(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyPrice p = new DailyPrice();
            p.setDate(LocalDate.now().minusDays(i));
            p.setOpen(BigDecimal.TEN);
            p.setHigh(BigDecimal.TEN);
            p.setLow(BigDecimal.TEN);
            p.setClose(BigDecimal.TEN);
            p.setVolume(1000L);
            prices.add(p);
        }
        return prices;
    }
}