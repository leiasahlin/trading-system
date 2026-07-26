package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import com.qullamaggie.tradingsystem.portfolio.SellRuleEvaluator;
import com.qullamaggie.tradingsystem.portfolio.service.PositionMonitoringService;
import com.qullamaggie.tradingsystem.portfolio.service.PositionSummaryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionMonitoringServiceTest {

    @Mock
    private PositionRepository positionRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private PositionAlertRepository positionAlertRepository;
    @Mock private PositionSummaryProvider positionSummaryProvider;
    @Mock private SellRuleEvaluator sellRuleEvaluator;

    private PositionMonitoringService positionMonitoringService;

    private Position position;
    private Stock stock;
    private Indicator indicator;
    private PositionSummary summary;

    @BeforeEach
    void setUp() {
        SellRuleConfig config = new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10);
        positionMonitoringService = new PositionMonitoringService(
                positionRepository, transactionRepository, indicatorRepository,
                positionAlertRepository, sellRuleEvaluator, positionSummaryProvider, config);

        stock = new Stock();
        stock.setSymbol("AAPL");

        position = new Position();
        position.setId(1L);
        position.setStock(stock);
        position.setStatus(PositionStatus.OPEN);
        position.setStopPrice(BigDecimal.valueOf(289));
        position.setInitialStopPrice(BigDecimal.valueOf(289));

        indicator = new Indicator();
        indicator.setMa10(BigDecimal.valueOf(310));

        summary = new PositionSummary(
                BigDecimal.valueOf(305), BigDecimal.valueOf(320), 200,
                BigDecimal.ZERO, BigDecimal.valueOf(3000), BigDecimal.valueOf(1.5));

        when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of(position));
        when(transactionRepository.findByPositionOrderByExecutedAtAsc(position)).thenReturn(List.of());
        when(positionSummaryProvider.buildSummary(position)).thenReturn(summary);
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));

        when(sellRuleEvaluator.shouldMoveStopToBreakeven(any(), any())).thenReturn(false);
        when(sellRuleEvaluator.shouldTrim(any(), any(), any(), any())).thenReturn(false);
        when(sellRuleEvaluator.calculateNewTrailingStop(any(), any())).thenReturn(Optional.empty());
        when(sellRuleEvaluator.shouldExit(any(), any())).thenReturn(false);
    }

    @Test
    void noRulesTriggered_createsNoAlerts() {
        positionMonitoringService.monitorAllOpenPositions();

        verifyNoInteractions(positionAlertRepository);
        verify(positionRepository, never()).save(any());
    }

    @Test
    void trimTriggered_createsTrimAlert_withoutMutatingStopPrice() {
        when(sellRuleEvaluator.shouldTrim(any(), any(), any(), any())).thenReturn(true);
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(position, PositionAlertType.TRIM, AlertStatus.NEW))
                .thenReturn(false);

        positionMonitoringService.monitorAllOpenPositions();

        ArgumentCaptor<PositionAlert> captor = ArgumentCaptor.forClass(PositionAlert.class);
        verify(positionAlertRepository).save(captor.capture());
        assertEquals(PositionAlertType.TRIM, captor.getValue().getType());
        assertNull(captor.getValue().getSuggestedStopPrice());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void breakevenTriggered_updatesStopPrice_andCreatesAlert() {
        when(sellRuleEvaluator.shouldMoveStopToBreakeven(any(), any())).thenReturn(true);
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(
                position, PositionAlertType.MOVE_STOP_TO_BREAKEVEN, AlertStatus.NEW)).thenReturn(false);

        positionMonitoringService.monitorAllOpenPositions();

        assertEquals(0, summary.averagePrice().compareTo(position.getStopPrice()));
        verify(positionRepository).save(position);

        ArgumentCaptor<PositionAlert> captor = ArgumentCaptor.forClass(PositionAlert.class);
        verify(positionAlertRepository).save(captor.capture());
        assertEquals(PositionAlertType.MOVE_STOP_TO_BREAKEVEN, captor.getValue().getType());
        assertEquals(0, summary.averagePrice().compareTo(captor.getValue().getSuggestedStopPrice()));
    }

    @Test
    void trailingStopTriggered_updatesStopPrice_andCreatesAlert() {
        BigDecimal newStop = BigDecimal.valueOf(315);
        when(sellRuleEvaluator.calculateNewTrailingStop(any(), any())).thenReturn(Optional.of(newStop));
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(
                position, PositionAlertType.TRAILING_STOP_UPDATE, AlertStatus.NEW)).thenReturn(false);

        positionMonitoringService.monitorAllOpenPositions();

        assertEquals(0, newStop.compareTo(position.getStopPrice()));
        verify(positionRepository).save(position);

        ArgumentCaptor<PositionAlert> captor = ArgumentCaptor.forClass(PositionAlert.class);
        verify(positionAlertRepository).save(captor.capture());
        assertEquals(PositionAlertType.TRAILING_STOP_UPDATE, captor.getValue().getType());
        assertEquals(0, newStop.compareTo(captor.getValue().getSuggestedStopPrice()));
    }

    @Test
    void exitTriggered_createsExitAlert() {
        when(sellRuleEvaluator.shouldExit(any(), any())).thenReturn(true);
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(position, PositionAlertType.EXIT, AlertStatus.NEW))
                .thenReturn(false);

        positionMonitoringService.monitorAllOpenPositions();

        ArgumentCaptor<PositionAlert> captor = ArgumentCaptor.forClass(PositionAlert.class);
        verify(positionAlertRepository).save(captor.capture());
        assertEquals(PositionAlertType.EXIT, captor.getValue().getType());
    }

    @Test
    void ruleTriggered_butAlertAlreadyExists_doesNotCreateDuplicate() {
        when(sellRuleEvaluator.shouldTrim(any(), any(), any(), any())).thenReturn(true);
        when(positionAlertRepository.existsByPositionAndTypeAndStatus(position, PositionAlertType.TRIM, AlertStatus.NEW))
                .thenReturn(true);

        positionMonitoringService.monitorAllOpenPositions();

        verify(positionAlertRepository, never()).save(any());
    }

    @Test
    void usesConfiguredMaPeriod_toFetchMa10() {
        positionMonitoringService.monitorAllOpenPositions();

        verify(sellRuleEvaluator).shouldExit(eq(BigDecimal.valueOf(310)), any());
    }
}
