package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.indicators.service.IndicatorService;
import com.qullamaggie.tradingsystem.portfolio.StockAlreadyExistsException;
import com.qullamaggie.tradingsystem.portfolio.service.StockUniverseService;
import com.qullamaggie.tradingsystem.universe.UniverseFilterConfig;
import com.qullamaggie.tradingsystem.universe.UniverseFilterEvaluator;
import com.qullamaggie.tradingsystem.universe.service.RelativeStrengthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockUniverseServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private MarketDataService marketDataService;
    @Mock private MarketDataProvider marketDataProvider;
    @Mock private IndicatorService indicatorService;
    @Mock private UniverseFilterEvaluator universeFilterEvaluator;
    @Mock private RelativeStrengthService relativeStrengthService;

    private StockUniverseService stockUniverseService;
    private DailyPrice price;
    private Indicator indicator;

    /** Aktier som rankas som ledare i de flesta tester. */
    private static final Set<String> LEADERS = Set.of("AAPL", "MSFT");

    @BeforeEach
    void setUp() {
        UniverseFilterConfig config = new UniverseFilterConfig(
                BigDecimal.valueOf(5),      // minPrice
                1_000_000L,                 // minAvgVolume
                BigDecimal.valueOf(4));     // minAdr

        stockUniverseService = new StockUniverseService(
                stockRepository, dailyPriceRepository, indicatorRepository,
                marketDataService, indicatorService, universeFilterEvaluator,
                marketDataProvider, config, relativeStrengthService);

        price = new DailyPrice();
        indicator = new Indicator();
    }

    // --- addStock ---

    @Test
    void addStock_throwsException_whenSymbolAlreadyExists() {
        when(stockRepository.existsBySymbol("AAPL")).thenReturn(true);

        assertThrows(StockAlreadyExistsException.class,
                () -> stockUniverseService.addStock("AAPL"));

        verifyNoInteractions(marketDataService, indicatorService);
        verify(stockRepository, never()).save(any());
    }

    @Test
    void addStock_setsSymbol_fetchesData_andEvaluatesEligibility() {
        when(stockRepository.existsBySymbol("AAPL")).thenReturn(false);
        when(relativeStrengthService.findLeaders()).thenReturn(LEADERS);
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(any())).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(any())).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);

        Stock result = stockUniverseService.addStock("AAPL");

        assertEquals("AAPL", result.getSymbol());
        assertTrue(result.isEligible());

        verify(marketDataService).refreshPrices(result);
        verify(indicatorService).calculateAndSaveIndicators(result);
        verify(stockRepository, times(2)).save(result);
    }

    // --- reEvaluateEligibility ---

    @Test
    void reEvaluateEligibility_leavesUnchanged_whenPriceDataMissing() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.empty());
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));

        stockUniverseService.reEvaluateEligibility(stock, LEADERS);

        assertFalse(stock.isEligible());
        verifyNoInteractions(universeFilterEvaluator);
        verify(stockRepository, never()).save(any());
    }

    @Test
    void reEvaluateEligibility_leavesUnchanged_whenIndicatorDataMissing() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.empty());

        stockUniverseService.reEvaluateEligibility(stock, LEADERS);

        assertFalse(stock.isEligible());
        verifyNoInteractions(universeFilterEvaluator);
        verify(stockRepository, never()).save(any());
    }

    @Test
    void reEvaluateEligibility_setsEligibleTrue_whenFilterPasses() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);

        stockUniverseService.reEvaluateEligibility(stock, LEADERS);

        assertTrue(stock.isEligible());
        verify(stockRepository).save(stock);
    }

    @Test
    void reEvaluateEligibility_setsEligibleFalse_whenFilterFails() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");
        stock.setEligible(true); // var behörig sen tidigare, ska nu bli det inte längre

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(false);

        stockUniverseService.reEvaluateEligibility(stock, LEADERS);

        assertFalse(stock.isEligible());
        verify(stockRepository).save(stock);
    }

    // --- reEvaluateAllStocks ---

    @Test
    void reEvaluateAllStocks_evaluatesEveryStock() {
        Stock stockA = new Stock();
        stockA.setSymbol("AAPL");
        Stock stockB = new Stock();
        stockB.setSymbol("MSFT");

        when(relativeStrengthService.findLeaders()).thenReturn(LEADERS);
        when(stockRepository.findAll()).thenReturn(List.of(stockA, stockB));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(any())).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(any())).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);

        stockUniverseService.reEvaluateAllStocks();

        verify(dailyPriceRepository).findTop1ByStockOrderByDateDesc(stockA);
        verify(dailyPriceRepository).findTop1ByStockOrderByDateDesc(stockB);
        verify(stockRepository).save(stockA);
        verify(stockRepository).save(stockB);
    }

    @Test
    void reEvaluateEligibility_setsEligibleFalse_whenNotAmongTheLeaders() {
        // Klarar filtret men saknar relativ styrka - alltså inte behörig
        Stock stock = new Stock();
        stock.setSymbol("TSLA");

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);

        stockUniverseService.reEvaluateEligibility(stock, LEADERS);

        assertFalse(stock.isEligible());
        verify(stockRepository).save(stock);
    }
}
