package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {
    List<DailyPrice> findByStockOrderByDateDesc(Stock stock);
    Optional<DailyPrice> findByStockAndDate(Stock stock, LocalDate date);
    Optional<DailyPrice> findTop1ByStockOrderByDateDesc(Stock stock);
    List<DailyPrice> findByStockSymbol(String symbol);
    List<DailyPrice> findByStockOrderByDateAsc(Stock stock);
    @Modifying
    @Query("delete from DailyPrice p where p.stock = :stock")
    int deleteByStock(@Param("stock") Stock stock);
}
