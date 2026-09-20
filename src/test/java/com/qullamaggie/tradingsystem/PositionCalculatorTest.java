package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.PositionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionCalculatorTest {

    private PositionCalculator calculator;
    private Position position;

    @BeforeEach
    void setUp() {
        calculator = new PositionCalculator();
        position = new Position();
        position.setInitialRisk(BigDecimal.valueOf(3200)); // 200 st, entry 305, stop 289
    }

    private Transaction transaction(TransactionType type, int shares, BigDecimal price, LocalDateTime executedAt) {
        Transaction t = new Transaction();
        t.setType(type);
        t.setShares(shares);
        t.setPrice(price);
        t.setExecutedAt(executedAt);
        return t;
    }

    @Test
    void singleBuy_noSells_averagePriceEqualsEntryPrice() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31))
        );

        PositionSummary summary = calculator.calculateSummary(position, transactions, BigDecimal.valueOf(320));

        assertEquals(0, BigDecimal.valueOf(305).compareTo(summary.averagePrice()));
        assertEquals(200, summary.remainingShares());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.realizedPnL()));
        assertEquals(0, BigDecimal.valueOf(3000).compareTo(summary.unrealizedPnL())); // (320-305)*200
    }

    @Test
    void partialSell_doesNotChangeAveragePrice() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.SELL, 100, BigDecimal.valueOf(320), LocalDateTime.of(2026, 1, 8, 15, 0))
        );

        PositionSummary summary = calculator.calculateSummary(position, transactions, BigDecimal.valueOf(320));

        assertEquals(0, BigDecimal.valueOf(305).compareTo(summary.averagePrice()));
        assertEquals(100, summary.remainingShares());
        assertEquals(0, BigDecimal.valueOf(1500).compareTo(summary.realizedPnL())); // (320-305)*100
    }

    @Test
    void twoSells_realizedPnLAccumulatesCorrectly() {
        // Regressionstest för buggen där kedjade .add().multiply()-anrop
        // multiplicerade upp det gamla realizedPnL vid varje ny sälj.
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.SELL, 100, BigDecimal.valueOf(320), LocalDateTime.of(2026, 1, 8, 15, 0)),
                transaction(TransactionType.SELL, 50, BigDecimal.valueOf(330), LocalDateTime.of(2026, 1, 12, 15, 0))
        );

        PositionSummary summary = calculator.calculateSummary(position, transactions, BigDecimal.valueOf(330));

        // (320-305)*100 + (330-305)*50 = 1500 + 1250 = 2750
        assertEquals(0, BigDecimal.valueOf(2750).compareTo(summary.realizedPnL()));
        assertEquals(50, summary.remainingShares());
    }

    @Test
    void pyramiding_recalculatesWeightedAveragePrice() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 100, BigDecimal.valueOf(300), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.BUY, 50, BigDecimal.valueOf(310), LocalDateTime.of(2026, 1, 7, 9, 31))
        );

        PositionSummary summary = calculator.calculateSummary(position, transactions, BigDecimal.valueOf(310));

        // (100*300 + 50*310) / 150 = 303.3333
        assertEquals(0, BigDecimal.valueOf(303.3333).compareTo(summary.averagePrice().setScale(4, RoundingMode.HALF_UP)));
        assertEquals(150, summary.remainingShares());
    }

    @Test
    void positionFullyClosed_remainingSharesIsZero_noUnrealizedPnL() {
        List<Transaction> transactions = List.of(
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31)),
                transaction(TransactionType.SELL, 200, BigDecimal.valueOf(353), LocalDateTime.of(2026, 1, 20, 15, 0))
        );

        PositionSummary summary = calculator.calculateSummary(position, transactions, BigDecimal.valueOf(353));

        assertEquals(0, summary.remainingShares());
        assertEquals(0, BigDecimal.valueOf(9600).compareTo(summary.realizedPnL())); // (353-305)*200
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.unrealizedPnL()));
        assertEquals(0, BigDecimal.valueOf(3).compareTo(summary.currentR())); // 9600 / 3200
    }

    @Test
    void unsortedInput_isSortedInternally() {
        // SELL skickas in FÖRE BUY i listan – om metoden inte sorterade
        // internt skulle SELL processas mot averagePrice = 0.
        List<Transaction> transactions = List.of(
                transaction(TransactionType.SELL, 100, BigDecimal.valueOf(320), LocalDateTime.of(2026, 1, 8, 15, 0)),
                transaction(TransactionType.BUY, 200, BigDecimal.valueOf(305), LocalDateTime.of(2026, 1, 5, 9, 31))
        );

        PositionSummary summary = calculator.calculateSummary(position, transactions, BigDecimal.valueOf(320));

        assertEquals(0, BigDecimal.valueOf(1500).compareTo(summary.realizedPnL()));
        assertEquals(100, summary.remainingShares());
    }

    @Test
    void emptyTransactionList_returnsZeroedSummary() {
        PositionSummary summary = calculator.calculateSummary(position, List.of(), BigDecimal.valueOf(100));

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.averagePrice()));
        assertEquals(0, summary.remainingShares());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.realizedPnL()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.unrealizedPnL()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.currentR()));
    }
}
