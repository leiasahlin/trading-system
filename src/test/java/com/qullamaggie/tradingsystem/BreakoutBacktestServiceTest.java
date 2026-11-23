package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.BreakoutBacktestService;
import com.qullamaggie.tradingsystem.backtest.BreakoutDay;
import com.qullamaggie.tradingsystem.backtest.BreakoutStats;
import com.qullamaggie.tradingsystem.backtest.DayStatus;
import com.qullamaggie.tradingsystem.backtest.RegimeHistory;
import com.qullamaggie.tradingsystem.backtest.StockHistoryEvaluator;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.entity.StockType;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BreakoutBacktestServiceTest {

    private static final LocalDate FROM = LocalDate.of(2020, 1, 2);

    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private RegimeHistory regimeHistory;
    @Mock private StockHistoryEvaluator stockHistoryEvaluator;

    private BreakoutBacktestService service;
    private Stock spy;
    private Stock aapl;
    private Stock tsla;

    @BeforeEach
    void setUp() {
        MarketRegimeConfig config = new MarketRegimeConfig(List.of("SPY"), RegimeMode.ANY, 10, 20);
        service = new BreakoutBacktestService(stockRepository, dailyPriceRepository,
                regimeHistory, stockHistoryEvaluator, config);

        spy = stock("SPY", StockType.INDEX);
        aapl = stock("AAPL", StockType.TRADEABLE);
        tsla = stock("TSLA", StockType.TRADEABLE);
    }

    @Test
    void shouldStopWhenIndexIsMissing() {
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.run(FROM));
        verifyNoInteractions(stockHistoryEvaluator);
    }

    @Test
    void shouldBuildRegimeFromIndexAndPassItOn() {
        List<DailyPrice> spyPrices = List.of(new DailyPrice());
        Map<LocalDate, RegimeStatus> regimes = Map.of(FROM, RegimeStatus.RISK_ON);
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(dailyPriceRepository.findByStockOrderByDateAsc(spy)).thenReturn(spyPrices);
        when(regimeHistory.build("SPY", spyPrices)).thenReturn(regimes);
        when(stockRepository.findAll()).thenReturn(List.of(aapl));
        when(dailyPriceRepository.findByStockOrderByDateAsc(aapl)).thenReturn(List.of());
        when(stockHistoryEvaluator.evaluate(eq("AAPL"), anyList(), eq(regimes), eq(FROM)))
                .thenReturn(List.of());

        service.run(FROM);

        verify(stockHistoryEvaluator).evaluate(eq("AAPL"), anyList(), eq(regimes), eq(FROM));
    }

    @Test
    void shouldSkipIndexAndUseEachStocksOwnSymbol() {
        givenIndexWithRegimes();
        when(stockRepository.findAll()).thenReturn(List.of(spy, aapl, tsla));
        when(dailyPriceRepository.findByStockOrderByDateAsc(aapl)).thenReturn(List.of());
        when(dailyPriceRepository.findByStockOrderByDateAsc(tsla)).thenReturn(List.of());
        when(stockHistoryEvaluator.evaluate(any(), anyList(), anyMap(), any()))
                .thenReturn(List.of());

        service.run(FROM);

        verify(stockHistoryEvaluator).evaluate(eq("AAPL"), anyList(), anyMap(), eq(FROM));
        verify(stockHistoryEvaluator).evaluate(eq("TSLA"), anyList(), anyMap(), eq(FROM));
        verify(stockHistoryEvaluator, never()).evaluate(eq("SPY"), anyList(), anyMap(), any());
    }

    @Test
    void shouldContinueWhenOneStockFails() {
        givenIndexWithRegimes();
        when(stockRepository.findAll()).thenReturn(List.of(aapl, tsla));
        when(dailyPriceRepository.findByStockOrderByDateAsc(aapl)).thenReturn(List.of());
        when(dailyPriceRepository.findByStockOrderByDateAsc(tsla)).thenReturn(List.of());
        when(stockHistoryEvaluator.evaluate(eq("AAPL"), anyList(), anyMap(), any()))
                .thenThrow(new RuntimeException("broken data"));
        BreakoutDay signal = new BreakoutDay("TSLA", LocalDate.of(2021, 1, 4),
                RegimeStatus.RISK_ON, DayStatus.EVALUATED, EnumSet.noneOf(BreakoutCriterion.class));
        when(stockHistoryEvaluator.evaluate(eq("TSLA"), anyList(), anyMap(), any()))
                .thenReturn(List.of(signal));

        BreakoutStats stats = service.run(FROM);

        assertEquals(List.of(signal), stats.interestingDays());
        assertEquals(1, stats.byYear().get(2021).signals());
    }

    private void givenIndexWithRegimes() {
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(dailyPriceRepository.findByStockOrderByDateAsc(spy)).thenReturn(List.of());
        when(regimeHistory.build(eq("SPY"), anyList())).thenReturn(Map.of());
    }

    private Stock stock(String symbol, StockType type) {
        Stock s = new Stock();
        s.setSymbol(symbol);
        s.setType(type);
        return s;
    }
}