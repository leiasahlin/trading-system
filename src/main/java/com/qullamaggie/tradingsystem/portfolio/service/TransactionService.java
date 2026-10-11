package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import com.qullamaggie.tradingsystem.data.repository.TransactionRepository;
import com.qullamaggie.tradingsystem.portfolio.PositionCalculator;
import com.qullamaggie.tradingsystem.portfolio.PositionClosedException;
import com.qullamaggie.tradingsystem.portfolio.PositionNotFoundException;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registers trims, exits and adds against an open position. BUY and SELL
 * are the actual order sides, so for a short a SELL adds and a BUY covers.
 *
 * Adds do not change initialRisk: R is always measured against what was
 * risked on the original entry (own quantification, the source document
 * says nothing about pyramiding).
 *
 * When the last share is sold the position is closed: STOPPED if the exit
 * was at or through the initial stop (the trade never worked), otherwise
 * CLOSED. outcomeR carries the actual result either way.
 */
@Service
public class TransactionService {

    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final PositionCalculator positionCalculator;

    public TransactionService(PositionRepository positionRepository,
                              TransactionRepository transactionRepository,
                              PositionCalculator positionCalculator) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.positionCalculator = positionCalculator;
    }

    /**
     * @param executedAt when the order filled, or null for now. Lets a trim
     *                   registered after the fact land in the right order
     *                   relative to other transactions.
     * @return the position, closed if this transaction emptied it
     * @throws PositionNotFoundException if no such position exists
     * @throws PositionClosedException   if the position is no longer open
     * @throws IllegalArgumentException  if the transaction is invalid, e.g.
     *                                   reduces by more shares than are held
     */
    @Transactional
    public Position record(Long positionId, TransactionType type, Integer shares,
                           BigDecimal price, LocalDateTime executedAt) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new PositionNotFoundException(positionId));

        if (position.getStatus() != PositionStatus.OPEN) {
            throw new PositionClosedException(positionId);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime actualExecutedAt = executedAt != null ? executedAt : now;
        validate(type, shares, price, actualExecutedAt, now);

        List<Transaction> existing = transactionRepository.findByPositionOrderByExecutedAtAsc(position);
        if (!existing.isEmpty() && actualExecutedAt.isBefore(existing.getFirst().getExecutedAt())) {
            throw new IllegalArgumentException("Transaktionen kan inte ligga före positionens öppning "
                    + existing.getFirst().getExecutedAt());
        }

        Transaction transaction = new Transaction();
        transaction.setPosition(position);
        transaction.setType(type);
        transaction.setShares(shares);
        transaction.setPrice(price);
        transaction.setExecutedAt(actualExecutedAt);

        List<Transaction> all = new ArrayList<>(existing);
        all.add(transaction);

        // Räknas ut innan något sparas: kalkylatorn kastar om innehavet skulle
        // bli negativt, så en ogiltig transaktion når aldrig databasen.
        // Kursen spelar ingen roll här - bara remainingShares och realizedPnL används.
        PositionSummary summary = positionCalculator.calculateSummary(position, all, price);

        transactionRepository.save(transaction);

        if (summary.remainingShares() == 0) {
            close(position, all, summary);
        }
        return position;
    }

    private void validate(TransactionType type, Integer shares, BigDecimal price,
                          LocalDateTime executedAt, LocalDateTime now) {
        if (type == null) {
            throw new IllegalArgumentException("type måste anges (BUY eller SELL)");
        }
        if (shares == null || shares <= 0) {
            throw new IllegalArgumentException("shares måste vara positivt, var: " + shares);
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price måste vara positivt, var: " + price);
        }
        if (executedAt.isAfter(now)) {
            throw new IllegalArgumentException("executedAt kan inte ligga i framtiden: " + executedAt);
        }
    }

    private void close(Position position, List<Transaction> all, PositionSummary summary) {
        List<Transaction> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(Transaction::getExecutedAt));
        Transaction exit = sorted.getLast();

        position.setStatus(hitInitialStop(position, exit.getPrice())
                ? PositionStatus.STOPPED
                : PositionStatus.CLOSED);
        position.setClosedAt(exit.getExecutedAt());
        // Inga aktier kvar, så currentR är rent realiserat resultat
        position.setOutcomeR(summary.currentR());
        positionRepository.save(position);
    }

    /** Long: exit at or below the initial stop. Short: at or above. */
    private boolean hitInitialStop(Position position, BigDecimal exitPrice) {
        int comparison = exitPrice.compareTo(position.getInitialStopPrice());
        return position.getSetupType().isShort() ? comparison >= 0 : comparison <= 0;
    }
}
