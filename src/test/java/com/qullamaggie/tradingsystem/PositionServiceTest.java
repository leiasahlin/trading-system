package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.PositionStatus;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.service.PositionService;
import com.qullamaggie.tradingsystem.portfolio.service.PositionSummaryProvider;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock private PositionRepository positionRepository;
    @Mock private PositionSummaryProvider positionSummaryProvider;

    @InjectMocks
    private PositionService positionService;

    private Position position;
    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = new Stock();
        stock.setSymbol("AAPL");

        position = new Position();
        position.setId(1L);
        position.setStock(stock);
        position.setStatus(PositionStatus.OPEN);
    }

    @Test
    void getSummary_returnsSummary_whenPositionExists() {
        PositionSummary expected = new PositionSummary(
                BigDecimal.valueOf(305), BigDecimal.valueOf(320), 200,
                BigDecimal.ZERO, BigDecimal.valueOf(3000), BigDecimal.valueOf(0.9375));

        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
        when(positionSummaryProvider.buildSummary(position)).thenReturn(expected);

        Optional<PositionSummary> result = positionService.getSummary(1L);

        assertEquals(Optional.of(expected), result);
        verify(positionSummaryProvider).buildSummary(position);
    }

    @Test
    void getSummary_returnsEmpty_whenPositionDoesNotExist() {
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<PositionSummary> result = positionService.getSummary(99L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(positionSummaryProvider);
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
        when(positionSummaryProvider.buildSummary(position)).thenReturn(summary1);
        when(positionSummaryProvider.buildSummary(secondPosition)).thenReturn(summary2);

        List<PositionSummary> results = positionService.getSummariesForAllOpenPositions();

        assertEquals(2, results.size());
        assertTrue(results.containsAll(List.of(summary1, summary2)));
    }

    @Test
    void getSummariesForAllOpenPositions_returnsEmptyList_whenNoOpenPositions() {
        when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of());

        List<PositionSummary> results = positionService.getSummariesForAllOpenPositions();

        assertTrue(results.isEmpty());
        verifyNoInteractions(positionSummaryProvider);
    }
}