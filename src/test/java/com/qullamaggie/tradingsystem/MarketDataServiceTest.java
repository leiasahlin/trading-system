package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MarketDataServiceTest {

    @Mock
    private MarketDataProvider marketDataProvider;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private MarketDataService marketDataService;

    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = new Stock();
        stock.setSymbol("AAPL");
    }

    @Test
    public void shouldFetchFullYearForNewStock() {
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.empty());

        DailyPrice fakePrice = new DailyPrice();
        fakePrice.setDate(LocalDate.now().minusDays(1));
        when(marketDataProvider.fetchDailyPrices(eq("AAPL"), anyInt()))
                .thenReturn(List.of(fakePrice));

        marketDataService.refreshPrices(stock);

        verify(dailyPriceRepository).saveAll(anyList());
    }

    @Test
    public void shouldFetchOnlyMissingDaysForExistingStock() {
        // Pretend the latest date in database is 3 days ago
        DailyPrice existingPrice = new DailyPrice();
        existingPrice.setDate(LocalDate.now().minusDays(3));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock))
                .thenReturn(Optional.of(existingPrice));

        //Pretend the provider returns data
        DailyPrice fakePrice = new DailyPrice();
        fakePrice.setDate(LocalDate.now().minusDays(2));
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
}
