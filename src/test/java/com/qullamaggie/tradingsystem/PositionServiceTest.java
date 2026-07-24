package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.portfolio.service.*;
import com.qullamaggie.tradingsystem.portfolio.*;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.portfolio.PositionCalculator;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock private PositionRepository positionRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private PositionCalculator positionCalculator;

    @InjectMocks
    private PositionService positionService;

    private Position position;
    private Stock stock;
    private DailyPrice latestPrice;

    @BeforeEach
    void setUp() {
        stock = new Stock();
        stock.setSymbol("AAPL");

        position = new Position();
        position.setId(1L);
        position.setStock(stock);
        position.setStatus(PositionStatus.OPEN);

        latestPrice = new DailyPrice();
        latestPrice.setClose(BigDecimal.valueOf(320));
    }

    @Test
    void getSummary_returnsEmpty_whenPositionDoesNotExist() {
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<PositionSummary> result = positionService.getSummary(99L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(transactionRepository, dailyPriceRepository, positionCalculator);
    }

    @Test
    void getSummary_throwsPositionNotFoundException_whenPositionDoesNotExist() {
        List<Transaction> transactions = List.of(new Transaction());
        PositionSummary expected = new PositionSummary(
                BigDecimal.valueOf(305), BigDecimal.valueOf(320), 200,
                BigDecimal.ZERO, BigDecimal.valueOf(3000), BigDecimal.valueOf(0.9375));

        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
        when(transactionRepository.findByPositionOrderByExecutedAtAsc(position)).thenReturn(transactions);
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(latestPrice));
        when(positionCalculator.calculateSummary(position, transactions, BigDecimal.valueOf(320)))
                .thenReturn(expected);

        Optional<PositionSummary> result = positionService.getSummary(1L);

        assertEquals(Optional.of(expected), result);
        verify(positionCalculator).calculateSummary(position, transactions, BigDecimal.valueOf(320));
    }

    @Test
    void getSummary_throwsIllegalStateException_whenNoPriceDataExists() {
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
        when(transactionRepository.findByPositionOrderByExecutedAtAsc(position)).thenReturn(List.of());
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> positionService.getSummary(1L));

        verifyNoInteractions(positionCalculator);
    }

    @Test
    void getSummariesForAllOpenPositions_returnsSummaryForEachOpenPosition() {
        Position secondPosition = new Position();
        secondPosition.setId(2L);
        secondPosition.setStock(stock);
        secondPosition.setStatus(PositionStatus.OPEN);

        PositionSummary summary1 = new PositionSummary(
                BigDecimal.valueOf(305), BigDecimal.valueOf(320), 200,
                BigDecimal.ZERO, BigDecimal.valueOf(3000), BigDecimal.valueOf(0.9375));
        PositionSummary summary2 = new PositionSummary(
                BigDecimal.valueOf(50), BigDecimal.valueOf(55), 100,
                BigDecimal.ZERO, BigDecimal.valueOf(500), BigDecimal.valueOf(0.5));

        when(positionRepository.findByStatus(PositionStatus.OPEN))
                .thenReturn(List.of(position, secondPosition));
        when(transactionRepository.findByPositionOrderByExecutedAtAsc(any())).thenReturn(List.of());
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(latestPrice));
        when(positionCalculator.calculateSummary(eq(position), any(), any())).thenReturn(summary1);
        when(positionCalculator.calculateSummary(eq(secondPosition), any(), any())).thenReturn(summary2);

        List<PositionSummary> results = positionService.getSummariesForAllOpenPositions();

        assertEquals(2, results.size());
        assertTrue(results.containsAll(List.of(summary1, summary2)));
    }

    @Test
    void getSummariesForAllOpenPositions_returnsEmptyList_whenNoOpenPositions() {
        when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of());

        List<PositionSummary> results = positionService.getSummariesForAllOpenPositions();

        assertTrue(results.isEmpty());
        verifyNoInteractions(positionCalculator);
    }
}
