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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockUniverseServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private MarketDataService marketDataService;
    @Mock private IndicatorService indicatorService;
    @Mock private UniverseFilterEvaluator universeFilterEvaluator;
    @Mock private MarketDataProvider marketDataProvider;

    private StockUniverseService stockUniverseService;

    private DailyPrice price;
    private Indicator indicator;

    @BeforeEach
    void setUp() {
        UniverseFilterConfig config = new UniverseFilterConfig(
                BigDecimal.valueOf(5),      // minPrice
                1_000_000L,                 // minAvgVolume
                BigDecimal.valueOf(4),      // minAdr
                7);                         // marketCapMaxAgeDays

        stockUniverseService = new StockUniverseService(
                stockRepository, dailyPriceRepository, indicatorRepository,
                marketDataService, indicatorService, universeFilterEvaluator,
                marketDataProvider, config);
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
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(any())).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(any())).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);

        Stock result = stockUniverseService.addStock("AAPL");

        assertEquals("AAPL", result.getSymbol());
        assertTrue(result.isEligible());

        verify(marketDataService).refreshPrices(result);
        verify(indicatorService).calculateAndSaveIndicators(result);
        // en gång direkt efter skapandet, en gång till efter behörighetsbedömningen
        verify(stockRepository, times(2)).save(result);
    }

    // --- reEvaluateEligibility ---

    @Test
    void reEvaluateEligibility_leavesUnchanged_whenPriceDataMissing() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.empty());
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));

        stockUniverseService.reEvaluateEligibility(stock);

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

        stockUniverseService.reEvaluateEligibility(stock);

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

        stockUniverseService.reEvaluateEligibility(stock);

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

        stockUniverseService.reEvaluateEligibility(stock);

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

    // --- market cap refresh ---

    @Test
    void reEvaluateEligibility_fetchesMarketCap_whenNeverFetchedBefore() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");
        // marketCapUpdatedAt är null som default

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);
        when(marketDataProvider.fetchMarketCap("AAPL")).thenReturn(BigDecimal.valueOf(50_000_000_000L));

        stockUniverseService.reEvaluateEligibility(stock);

        assertEquals(0, BigDecimal.valueOf(50_000_000_000L).compareTo(stock.getMarketCapUsd()));
        assertEquals(LocalDate.now(), stock.getMarketCapUpdatedAt());
    }

    @Test
    void reEvaluateEligibility_skipsMarketCapFetch_whenValueIsFresh() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");
        stock.setMarketCapUsd(BigDecimal.valueOf(50_000_000_000L));
        stock.setMarketCapUpdatedAt(LocalDate.now().minusDays(2)); // under 7-dagarströskeln

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);

        stockUniverseService.reEvaluateEligibility(stock);

        verifyNoInteractions(marketDataProvider);
        assertEquals(LocalDate.now().minusDays(2), stock.getMarketCapUpdatedAt());
    }

    @Test
    void reEvaluateEligibility_fetchesMarketCap_whenValueIsStale() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");
        stock.setMarketCapUsd(BigDecimal.valueOf(40_000_000_000L));
        stock.setMarketCapUpdatedAt(LocalDate.now().minusDays(10)); // över tröskeln

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);
        when(marketDataProvider.fetchMarketCap("AAPL")).thenReturn(BigDecimal.valueOf(55_000_000_000L));

        stockUniverseService.reEvaluateEligibility(stock);

        assertEquals(0, BigDecimal.valueOf(55_000_000_000L).compareTo(stock.getMarketCapUsd()));
        assertEquals(LocalDate.now(), stock.getMarketCapUpdatedAt());
    }

    @Test
    void reEvaluateEligibility_leavesTimestampUnset_whenMarketCapFetchReturnsNull() {
        // Gratisplanen ger ingen fundamentaldata - stämpeln ska INTE sättas,
        // så att systemet försöker igen nästa körning i stället för att låsa
        // ute aktien i en vecka.
        Stock stock = new Stock();
        stock.setSymbol("AAPL");

        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(universeFilterEvaluator.isEligible(price, indicator)).thenReturn(true);
        when(marketDataProvider.fetchMarketCap("AAPL")).thenReturn(null);

        stockUniverseService.reEvaluateEligibility(stock);

        assertNull(stock.getMarketCapUsd());
        assertNull(stock.getMarketCapUpdatedAt());
    }
}
