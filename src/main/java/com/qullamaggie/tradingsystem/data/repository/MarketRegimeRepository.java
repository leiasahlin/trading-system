package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.MarketRegime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MarketRegimeRepository extends JpaRepository<MarketRegime, Long> {
    Optional<MarketRegime> findByDate(LocalDate date);
    Optional<MarketRegime> findTop1ByOrderByDateDesc();
}