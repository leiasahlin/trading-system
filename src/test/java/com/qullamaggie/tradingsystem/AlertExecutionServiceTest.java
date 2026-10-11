package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import com.qullamaggie.tradingsystem.data.repository.TransactionRepository;
import com.qullamaggie.tradingsystem.portfolio.service.AlertExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertExecutionServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private AlertExecutionService service;

    private Alert alert;

    @BeforeEach
    void setUp() {
        Stock stock = new Stock();
        stock.setSymbol("MSTR");

        alert = new Alert();
        alert.setId(1L);
        alert.setStock(stock);
        alert.setShares(100);
        alert.setStatus(AlertStatus.NEW);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void longSetup_opensWithBuy() {
        alert.setType("BREAKOUT");
        alert.setEntryPrice(BigDecimal.valueOf(100));
        alert.setStopPrice(BigDecimal.valueOf(95));

        Position position = service.execute(1L, null, null);

        assertEquals(SetupType.BREAKOUT, position.getSetupType());
        assertEquals(TransactionType.BUY, savedTransaction().getType());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(position.getInitialRisk()));
    }

    @Test
    void parabolicShort_opensWithSell() {
        alert.setType("PARABOLIC_SHORT");
        alert.setEntryPrice(BigDecimal.valueOf(50));
        alert.setStopPrice(BigDecimal.valueOf(55));

        Position position = service.execute(1L, null, null);

        assertEquals(SetupType.PARABOLIC_SHORT, position.getSetupType());
        assertEquals(TransactionType.SELL, savedTransaction().getType());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(position.getInitialRisk()));
    }
}
