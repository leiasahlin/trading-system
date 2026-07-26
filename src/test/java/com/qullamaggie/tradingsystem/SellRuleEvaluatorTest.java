package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import com.qullamaggie.tradingsystem.portfolio.SellRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SellRuleEvaluatorTest {
    private SellRuleEvaluator evaluator;
    private Position position;
    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 1, 5);

    @BeforeEach
    void setUp() {
        evaluator = new SellRuleEvaluator(new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10));
        position = new Position();
        position.setInitialStopPrice(BigDecimal.valueOf(289));
        position.setStopPrice(BigDecimal.valueOf(289));
        position.setOpenedAt(ENTRY_DATE.atStartOfDay());
    }

    private Transaction transaction(TransactionType type, int shares, BigDecimal price, LocalDateTime executedAt) {
        Transaction t = new Transaction();
        t.setType(type);
        t.setShares(shares);
        t.setPrice(price);
        t.setExecutedAt(executedAt);
        return t;
    }

    private PositionSummary summaryWithR(BigDecimal currentR) {
        return new PositionSummary(
                BigDecimal.valueOf(305), BigDecimal.valueOf(320), 200,
                BigDecimal.ZERO, BigDecimal.ZERO, currentR);
    }

    // --- shouldMoveStopToBreakeven (befintliga tester, oförändrade) ---

    @Test
    void noPartialSell_stopNotMoved_returnsFalse() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31))
        );
        assertFalse(evaluator.shouldMoveStopToBreakeven(position, transactions));
    }

    @Test
    void noPartialSell_stopAlreadyMoved_returnsFalse() {
        position.setStopPrice(BigDecimal.valueOf(305));
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31))
        );
        assertFalse(evaluator.shouldMoveStopToBreakeven(position, transactions));
    }

    @Test
    void partialSell_stopNotMoved_returnsTrue() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.SELL, 100, BigDecimal.valueOf(320), LocalDateTime.of(2026, 1, 8, 15, 0))
        );
        assertTrue(evaluator.shouldMoveStopToBreakeven(position, transactions));
    }

    @Test
    void partialSell_stopAlreadyMoved_returnsFalse() {
        position.setStopPrice(BigDecimal.valueOf(305));
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.SELL, 100, BigDecimal.valueOf(320), LocalDateTime.of(2026, 1, 8, 15, 0))
        );
        assertFalse(evaluator.shouldMoveStopToBreakeven(position, transactions));
    }

    // --- shouldTrim (nya tester) ---

    @Test
    void alreadyTrimmed_returnsFalse_regardlessOfRAndDays() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.SELL, 100, BigDecimal.valueOf(320), LocalDateTime.of(2026, 1, 8, 15, 0))
        );
        // R=5 och 10 dagar hade annars gett true på båda delvillkoren
        PositionSummary summary = summaryWithR(BigDecimal.valueOf(5));
        LocalDate asOfDate = ENTRY_DATE.plusDays(10);

        assertFalse(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    @Test
    void rReached_andWithinDayWindow_returnsTrue() {
        List<Transaction> transactions = List.of();
        PositionSummary summary = summaryWithR(BigDecimal.valueOf(2));
        LocalDate asOfDate = ENTRY_DATE.plusDays(4);

        assertTrue(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    @Test
    void rReached_butOutsideDayWindow_returnsTrue_becauseROnSufficies() {
        List<Transaction> transactions = List.of();
        PositionSummary summary = summaryWithR(BigDecimal.valueOf(2));
        LocalDate asOfDate = ENTRY_DATE.plusDays(1); // för tidigt för dagfönstret

        assertTrue(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    @Test
    void rNotReached_butWithinDayWindow_returnsTrue_becauseDaysAloneSuffice() {
        List<Transaction> transactions = List.of();
        PositionSummary summary = summaryWithR(BigDecimal.ZERO);
        LocalDate asOfDate = ENTRY_DATE.plusDays(4);

        assertTrue(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    @Test
    void neitherRNorDayWindow_returnsFalse() {
        List<Transaction> transactions = List.of();
        PositionSummary summary = summaryWithR(BigDecimal.ZERO);
        LocalDate asOfDate = ENTRY_DATE.plusDays(1);

        assertFalse(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    @Test
    void exactlyMinDaysHeld_isInclusive_returnsTrue() {
        List<Transaction> transactions = List.of();
        PositionSummary summary = summaryWithR(BigDecimal.ZERO);
        LocalDate asOfDate = ENTRY_DATE.plusDays(3); // == minDaysHeld

        assertTrue(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    @Test
    void exactlyMaxDaysHeld_isInclusive_returnsTrue() {
        List<Transaction> transactions = List.of();
        PositionSummary summary = summaryWithR(BigDecimal.ZERO);
        LocalDate asOfDate = ENTRY_DATE.plusDays(5); // == maxDaysHeld

        assertTrue(evaluator.shouldTrim(position, transactions, summary, asOfDate));
    }

    // --- calculateNewTrailingStop ---

    @Test
    void notYetPastBreakeven_returnsEmpty_regardlessOfMaValue() {
        Optional<BigDecimal> result = evaluator.calculateNewTrailingStop(position, BigDecimal.valueOf(310));

        assertTrue(result.isEmpty());
    }

    @Test
    void pastBreakeven_maLowerThanCurrentStop_returnsEmpty() {
        position.setStopPrice(BigDecimal.valueOf(305)); // already moved to breakeven

        Optional<BigDecimal> result = evaluator.calculateNewTrailingStop(position, BigDecimal.valueOf(300));

        assertTrue(result.isEmpty());
    }

    @Test
    void pastBreakeven_maEqualToCurrentStop_returnsEmpty() {
        position.setStopPrice(BigDecimal.valueOf(305));

        Optional<BigDecimal> result = evaluator.calculateNewTrailingStop(position, BigDecimal.valueOf(305));

        assertTrue(result.isEmpty());
    }

    @Test
    void pastBreakeven_maHigherThanCurrentStop_returnsNewStopValue() {
        position.setStopPrice(BigDecimal.valueOf(305));

        Optional<BigDecimal> result = evaluator.calculateNewTrailingStop(position, BigDecimal.valueOf(315));

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(315).compareTo(result.get()));
    }

// --- shouldExit ---

    @Test
    void closeBelowMa_returnsTrue() {
        assertTrue(evaluator.shouldExit(BigDecimal.valueOf(315), BigDecimal.valueOf(310)));
    }

    @Test
    void closeAboveMa_returnsFalse() {
        assertFalse(evaluator.shouldExit(BigDecimal.valueOf(315), BigDecimal.valueOf(320)));
    }

    @Test
    void closeEqualsMa_returnsFalse() {
        assertFalse(evaluator.shouldExit(BigDecimal.valueOf(315), BigDecimal.valueOf(315)));
    }
}
