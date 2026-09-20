package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.dto.IntradayBar;
import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.PositionAlert;
import com.qullamaggie.tradingsystem.data.entity.PositionStatus;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.PositionAlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import com.qullamaggie.tradingsystem.portfolio.service.IntradayMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntradayMonitoringServiceTest {

    @Mock private PositionRepository positionRepository;
    @Mock private PositionAlertRepository positionAlertRepository;
    @Mock private MarketDataProvider marketDataProvider;

    @InjectMocks
    private IntradayMonitoringService service;

    private Position position;

    @BeforeEach
    void setUp() {
        Stock stock = new Stock();
        stock.setSymbol("AAPL");
        position = new Position();
        position.setStock(stock);
        position.setStopPrice(new BigDecimal("290"));
        when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of(position));
    }

    private IntradayBar barClosingAt(String close) {
        return new IntradayBar(LocalDateTime.of(2026, 8, 3, 10, 0),
                new BigDecimal(close), new BigDecimal(close),
                new BigDecimal(close), new BigDecimal(close), 1000);
    }

    @Test
    void alertsWhenLastCloseAtOrBelowStop() {
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(any(), any(), any())).thenReturn(false);
        when(marketDataProvider.fetchIntradayBars("AAPL")).thenReturn(List.of(barClosingAt("289")));

        service.checkAllOpenPositions();

        verify(positionAlertRepository).save(any(PositionAlert.class));
    }

    @Test
    void doesNotAlert_whenPriceAboveStop() {
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(any(), any(), any())).thenReturn(false);
        when(marketDataProvider.fetchIntradayBars("AAPL")).thenReturn(List.of(barClosingAt("295")));

        service.checkAllOpenPositions();

        verify(positionAlertRepository, never()).save(any());
    }

    @Test
    void doesNotAlertTwice_whenPendingBreachAlertExists() {
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(any(), any(), any())).thenReturn(true);

        service.checkAllOpenPositions();

        verify(marketDataProvider, never()).fetchIntradayBars(any());
        verify(positionAlertRepository, never()).save(any());
    }

    @Test
    void skipsPosition_withoutStopPrice() {
        position.setStopPrice(null);

        service.checkAllOpenPositions();

        verifyNoInteractions(marketDataProvider);
        verify(positionAlertRepository, never()).save(any());
    }
}
