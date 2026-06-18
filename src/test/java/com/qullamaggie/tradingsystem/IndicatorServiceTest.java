package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @InjectMocks
    private IndicatorService indicatorService;

    private Stock stock;

    @BeforeEach
    public void setUp() {
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
                .thenReturn(buildPrices(60));
        when(indicatorRepository.findByStockAndDate(eq(stock), any()))
                .thenReturn(Optional.empty());

        indicatorService.calculateAndSaveIndicators(stock);

        verify(indicatorRepository).save(any(Indicator.class));
    }

    @Test
    public void shouldReuseExistingIndicatorRow() {
        Indicator existing = new Indicator();
        existing.setStock(stock);

        when(dailyPriceRepository.findByStockOrderByDateDesc(stock))
                .thenReturn(buildPrices(60));
        when(indicatorRepository.findByStockAndDate(eq(stock), any()))
                .thenReturn(Optional.of(existing));

        indicatorService.calculateAndSaveIndicators(stock);

        verify(indicatorRepository).save(existing);
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
