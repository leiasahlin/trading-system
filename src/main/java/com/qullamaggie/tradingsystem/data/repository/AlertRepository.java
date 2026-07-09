package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.Alert;
import com.qullamaggie.tradingsystem.data.entity.AlertStatus;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findBySeen(boolean seen);
    boolean existsByStockAndStatus(Stock stock, AlertStatus status);
}
