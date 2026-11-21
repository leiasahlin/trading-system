package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.MarketDataConfig;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import com.qullamaggie.tradingsystem.data.service.PriceHistoryWriter;
import com.qullamaggie.tradingsystem.data.service.SplitAdjuster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MarketDataServiceTest {

    @Mock private MarketDataProvider marketDataProvider;
    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private PriceHistoryWriter priceHistoryWriter;

    private MarketDataService marketDataService;
    private Stock stock;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService(
                marketDataProvider, stockRepository, dailyPriceRepository, new SplitAdjuster(),
                new MarketDataConfig(LocalDate.of(2019, 12, 1)),
                priceHistoryWriter);

        stock = new Stock();
        stock.setSymbol("AAPL");
    }

    @Test
    public void shouldFetchFromHistoryStartForNewStock() {
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.empty());

        DailyPrice fakePrice = new DailyPrice();
        fakePrice.setDate(LocalDate.now().minusDays(1));
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt()))
                .thenReturn(List.of(fakePrice));

        marketDataService.refreshPrices(stock);

        // The number of days asked for must reach all the way back to history-start
        int expectedDays = (int) ChronoUnit.DAYS.between(LocalDate.of(2019, 12, 1), LocalDate.now());
        verify(marketDataProvider).fetchDailyPrices("AAPL", expectedDays);
        verify(dailyPriceRepository).saveAll(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldDropPricesBeforeHistoryStart() {
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.empty());

        DailyPrice tooOld = new DailyPrice();
        tooOld.setDate(LocalDate.of(2019, 11, 29));
        tooOld.setClose(BigDecimal.TEN);
        DailyPrice onStart = new DailyPrice();
        onStart.setDate(LocalDate.of(2019, 12, 2));
        onStart.setClose(BigDecimal.TEN);
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt()))
                .thenReturn(List.of(tooOld, onStart));

        marketDataService.refreshPrices(stock);

        ArgumentCaptor<List<DailyPrice>> saved = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).saveAll(saved.capture());
        assertEquals(List.of(onStart), saved.getValue());
    }

    @Test
    public void shouldFetchOnlyMissingDaysForExistingStock() {
        // Pretend the latest date in database is 3 days ago
        DailyPrice existingPrice = new DailyPrice();
        existingPrice.setDate(LocalDate.now().minusDays(3));
        existingPrice.setClose(new BigDecimal("100"));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(existingPrice));

        //Pretend the provider returns data
        DailyPrice fakePrice = new DailyPrice();
        fakePrice.setDate(LocalDate.now().minusDays(2));
        fakePrice.setClose(new BigDecimal("101"));
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt()))
                .thenReturn(List.of(fakePrice));

        marketDataService.refreshPrices(stock);

        verify(marketDataProvider).fetchDailyPrices(eq("AAPL"), eq(2));
        verify(dailyPriceRepository).saveAll(anyList());
    }

    @Test
    public void shouldDoNothingWhenDataIsUpToDate() {
        // Låtsas att senaste datum är idag
        DailyPrice existingPrice = new DailyPrice();
        existingPrice.setDate(LocalDate.now());
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(existingPrice));

        marketDataService.refreshPrices(stock);

        // Verifiera att varken providern eller saveAll anropades
        verify(marketDataProvider, never()).fetchDailyPrices(any(), anyInt());
        verify(dailyPriceRepository, never()).saveAll(anyList());
    }

    @Test
    public void shouldHandleNoNewPricesOnMarketHoliday() {
        // Last stored day is yesterday, but the market was closed today
        DailyPrice existingPrice = new DailyPrice();
        existingPrice.setDate(LocalDate.now().minusDays(2));
        existingPrice.setClose(new BigDecimal("100"));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(existingPrice));
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt()))
                .thenReturn(List.of());

        int saved = marketDataService.refreshPrices(stock);

        assertEquals(0, saved);
        // No split check without a new day to compare against
        verify(marketDataProvider, never()).fetchSplits(any());
    }

    @Test
    public void reseedShouldReplaceHistoryWhenNewIsLonger() {
        List<DailyPrice> fetched = pricesFrom(LocalDate.of(2019, 12, 2), 1750);
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt())).thenReturn(fetched);
        when(dailyPriceRepository.countByStock(stock)).thenReturn(250L);
        when(priceHistoryWriter.replaceHistory(eq(stock), anyList())).thenReturn(250);

        int saved = marketDataService.reseedHistory(stock);

        assertEquals(1750, saved);
        verify(priceHistoryWriter).replaceHistory(eq(stock), argThat(list -> list.size() == 1750));
    }

    @Test
    public void reseedShouldKeepOldHistoryWhenNewIsShorter() {
        List<DailyPrice> fetched = pricesFrom(LocalDate.now().minusDays(30), 30);
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt())).thenReturn(fetched);
        when(dailyPriceRepository.countByStock(stock)).thenReturn(250L);

        int saved = marketDataService.reseedHistory(stock);

        assertEquals(0, saved);
        verify(priceHistoryWriter, never()).replaceHistory(any(), anyList());
    }

    @Test
    public void reseedShouldKeepOldHistoryWhenFetchReturnsNothing() {
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt())).thenReturn(List.of());
        when(dailyPriceRepository.countByStock(stock)).thenReturn(250L);

        int saved = marketDataService.reseedHistory(stock);

        assertEquals(0, saved);
        verify(priceHistoryWriter, never()).replaceHistory(any(), anyList());
    }

    /** Builds consecutive daily prices, oldest first, with a flat close of 10. */
    private List<DailyPrice> pricesFrom(LocalDate start, int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyPrice p = new DailyPrice();
            p.setDate(start.plusDays(i));
            p.setClose(BigDecimal.TEN);
            prices.add(p);
        }
        return prices;
    }
}
