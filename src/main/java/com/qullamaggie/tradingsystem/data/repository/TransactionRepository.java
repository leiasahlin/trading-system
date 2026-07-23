package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;

import java.util.List;

public interface TransactionRepository {
    /**
     * Finds all transactions for a position, oldest first.
     * Order matters when deriving average price and remaining shares.
     */
    List<Transaction> findByPositionOrderByExecutedAtAsc(Position position);
}
