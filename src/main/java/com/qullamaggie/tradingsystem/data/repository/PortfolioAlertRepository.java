package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.PortfolioAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioAlertRepository extends JpaRepository<PortfolioAlert, Long> {
    boolean existsByIsinAndAlertDate(String isin, LocalDate alertDate);
    /** Overnight alerts not yet included in any notification email. */
    List<PortfolioAlert> findByNotifiedAtIsNull();
}