package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);
}
