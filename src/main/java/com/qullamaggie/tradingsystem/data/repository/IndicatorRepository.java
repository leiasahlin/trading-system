package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
    Optional<Indicator> findByStockAndDate(Stock stock, LocalDate date);
    Optional<Indicator> findTop1ByStockOrderByDateDesc(Stock stock);
}
