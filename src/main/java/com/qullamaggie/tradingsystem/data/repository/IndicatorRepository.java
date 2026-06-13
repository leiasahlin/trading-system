package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
    Optional<Indicator> findTopByStockOrderByDateDesc(Stock stock);
}
