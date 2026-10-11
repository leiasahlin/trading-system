package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import com.qullamaggie.tradingsystem.data.repository.TransactionRepository;
import com.qullamaggie.tradingsystem.portfolio.PositionCalculator;
import com.qullamaggie.tradingsystem.portfolio.PositionClosedException;
import com.qullamaggie.tradingsystem.portfolio.PositionNotFoundException;
import com.qullamaggie.tradingsystem.portfolio.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Uses the real PositionCalculator so the closing logic is tested against
 * actual PnL numbers rather than stubbed summaries.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final LocalDateTime OPENED = LocalDateTime.of(2026, 10, 1, 9, 31);

    @Mock private PositionRepository positionRepository;
    @Mock private TransactionRepository transactionRepository;

    private TransactionService service;
    private Position position;
    private List<Transaction> existing;

    @BeforeEach
    void setUp() {
        service = new TransactionService(positionRepository, transactionRepository, new PositionCalculator());

        // Long: 200 st à 100, stop 95 -> initial risk 1000
        position = new Position();
        position.setId(1L);
        position.setSetupType(SetupType.BREAKOUT);
        position.setStatus(PositionStatus.OPEN);
        position.setStopPrice(BigDecimal.valueOf(95));
        position.setInitialStopPrice(BigDecimal.valueOf(95));
        position.setInitialRisk(BigDecimal.valueOf(1000));

        existing = new ArrayList<>();
        existing.add(transaction(TransactionType.BUY, 200, BigDecimal.valueOf(100), OPENED));
    }

    private Transaction transaction(TransactionType type, int shares, BigDecimal price, LocalDateTime executedAt) {
        Transaction t = new Transaction();
        t.setPosition(position);
        t.setType(type);
        t.setShares(shares);
        t.setPrice(price);
        t.setExecutedAt(executedAt);
        return t;
    }

    private void givenPositionWithExisting() {
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
        when(transactionRepository.findByPositionOrderByExecutedAtAsc(position)).thenReturn(existing);
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }

    // --- Trim och tillägg ---

    @Test
    void partialSell_isSaved_positionStaysOpen() {
        givenPositionWithExisting();

        service.record(1L, TransactionType.SELL, 100, BigDecimal.valueOf(110), OPENED.plusDays(3));

        Transaction saved = savedTransaction();
        assertEquals(TransactionType.SELL, saved.getType());
        assertEquals(100, saved.getShares());
        assertEquals(position, saved.getPosition());
        assertEquals(PositionStatus.OPEN, position.getStatus());
        assertNull(position.getClosedAt());
        assertNull(position.getOutcomeR());
        verify(positionRepository, never()).save(any());
    }

    @Test
    void add_isSaved_initialRiskUnchanged() {
        givenPositionWithExisting();

        service.record(1L, TransactionType.BUY, 100, BigDecimal.valueOf(108), OPENED.plusDays(2));

        assertEquals(TransactionType.BUY, savedTransaction().getType());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(position.getInitialRisk()));
        assertEquals(PositionStatus.OPEN, position.getStatus());
    }

    @Test
    void executedAtNull_defaultsToNow() {
        givenPositionWithExisting();
        LocalDateTime before = LocalDateTime.now();

        service.record(1L, TransactionType.SELL, 50, BigDecimal.valueOf(110), null);

        LocalDateTime executedAt = savedTransaction().getExecutedAt();
        assertFalse(executedAt.isBefore(before));
        assertFalse(executedAt.isAfter(LocalDateTime.now()));
    }

    // --- Stängning ---

    @Test
    void sellingLastShares_aboveInitialStop_closesAsClosed() {
        existing.add(transaction(TransactionType.SELL, 100, BigDecimal.valueOf(110), OPENED.plusDays(3)));
        givenPositionWithExisting();
        LocalDateTime exitTime = OPENED.plusDays(10);

        service.record(1L, TransactionType.SELL, 100, BigDecimal.valueOf(120), exitTime);

        assertEquals(PositionStatus.CLOSED, position.getStatus());
        assertEquals(exitTime, position.getClosedAt());
        // (110-100)*100 + (120-100)*100 = 3000 -> 3R
        assertEquals(0, BigDecimal.valueOf(3).compareTo(position.getOutcomeR()));
        verify(positionRepository).save(position);
    }

    @Test
    void breakevenExit_isClosedNotStopped() {
        // Stoppen flyttad till breakeven och träffad: inte STOPPED, eftersom
        // den ursprungliga stoppen aldrig nåddes
        position.setStopPrice(BigDecimal.valueOf(100));
        givenPositionWithExisting();

        service.record(1L, TransactionType.SELL, 200, BigDecimal.valueOf(100), OPENED.plusDays(5));

        assertEquals(PositionStatus.CLOSED, position.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(position.getOutcomeR()));
    }

    @Test
    void sellingLastShares_atInitialStop_closesAsStopped() {
        givenPositionWithExisting();

        service.record(1L, TransactionType.SELL, 200, BigDecimal.valueOf(95), OPENED.plusDays(1));

        assertEquals(PositionStatus.STOPPED, position.getStatus());
        assertEquals(0, BigDecimal.valueOf(-1).compareTo(position.getOutcomeR()));
    }

    @Test
    void sellingLastShares_belowInitialStop_slippage_closesAsStopped() {
        givenPositionWithExisting();

        service.record(1L, TransactionType.SELL, 200, BigDecimal.valueOf(94), OPENED.plusDays(1));

        assertEquals(PositionStatus.STOPPED, position.getStatus());
        assertEquals(0, new BigDecimal("-1.2").compareTo(position.getOutcomeR())); // -1200 / 1000
    }

    @Test
    void backdatedTrim_closedAtIsLatestTransaction_notTheRegisteredOne() {
        LocalDateTime laterExit = OPENED.plusDays(10);
        existing.add(transaction(TransactionType.SELL, 100, BigDecimal.valueOf(120), laterExit));
        givenPositionWithExisting();

        // Trimmen glömdes bort och registreras i efterhand, före exit
        service.record(1L, TransactionType.SELL, 100, BigDecimal.valueOf(110), OPENED.plusDays(3));

        assertEquals(PositionStatus.CLOSED, position.getStatus());
        assertEquals(laterExit, position.getClosedAt());
    }

    // --- Blankning ---

    @Test
    void short_coverThroughInitialStop_closesAsStopped() {
        // Short: 100 st à 50, stop 55 -> initial risk 500
        position.setSetupType(SetupType.PARABOLIC_SHORT);
        position.setStopPrice(BigDecimal.valueOf(55));
        position.setInitialStopPrice(BigDecimal.valueOf(55));
        position.setInitialRisk(BigDecimal.valueOf(500));
        existing.clear();
        existing.add(transaction(TransactionType.SELL, 100, BigDecimal.valueOf(50), OPENED));
        givenPositionWithExisting();

        service.record(1L, TransactionType.BUY, 100, BigDecimal.valueOf(56), OPENED.plusDays(1));

        assertEquals(PositionStatus.STOPPED, position.getStatus());
        assertEquals(0, new BigDecimal("-1.2").compareTo(position.getOutcomeR())); // (50-56)*100 / 500
    }

    @Test
    void short_coverBelowEntry_closesAsClosedWithProfit() {
        position.setSetupType(SetupType.PARABOLIC_SHORT);
        position.setStopPrice(BigDecimal.valueOf(55));
        position.setInitialStopPrice(BigDecimal.valueOf(55));
        position.setInitialRisk(BigDecimal.valueOf(500));
        existing.clear();
        existing.add(transaction(TransactionType.SELL, 100, BigDecimal.valueOf(50), OPENED));
        givenPositionWithExisting();

        service.record(1L, TransactionType.BUY, 100, BigDecimal.valueOf(40), OPENED.plusDays(4));

        assertEquals(PositionStatus.CLOSED, position.getStatus());
        assertEquals(0, BigDecimal.valueOf(2).compareTo(position.getOutcomeR())); // 1000 / 500
    }

    // --- Valideringar ---

    @Test
    void sellingMoreThanHeld_throws_nothingSaved() {
        givenPositionWithExisting();

        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, TransactionType.SELL, 250, BigDecimal.valueOf(110), OPENED.plusDays(3)));

        verify(transactionRepository, never()).save(any());
        assertEquals(PositionStatus.OPEN, position.getStatus());
    }

    @Test
    void positionNotFound_throws() {
        when(positionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(PositionNotFoundException.class,
                () -> service.record(99L, TransactionType.SELL, 100, BigDecimal.valueOf(110), null));
    }

    @Test
    void closedPosition_throws() {
        position.setStatus(PositionStatus.CLOSED);
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));

        assertThrows(PositionClosedException.class,
                () -> service.record(1L, TransactionType.SELL, 100, BigDecimal.valueOf(110), null));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void zeroShares_throws() {
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, TransactionType.SELL, 0, BigDecimal.valueOf(110), null));
    }

    @Test
    void negativePrice_throws() {
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, TransactionType.SELL, 100, BigDecimal.valueOf(-1), null));
    }

    @Test
    void missingType_throws() {
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, null, 100, BigDecimal.valueOf(110), null));
    }

    @Test
    void executedAtInFuture_throws() {
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));

        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, TransactionType.SELL, 100, BigDecimal.valueOf(110),
                        LocalDateTime.now().plusDays(1)));
    }

    @Test
    void executedAtBeforeOpening_throws() {
        givenPositionWithExisting();

        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, TransactionType.BUY, 100, BigDecimal.valueOf(98), OPENED.minusDays(1)));
        verify(transactionRepository, never()).save(any());
    }
}
