package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    /**
     * Finds all transactions for a position, oldest first.
     * Order matters when deriving average price and remaining shares.
     */
    List<Transaction> findByPositionOrderByExecutedAtAsc(Position position);
}
