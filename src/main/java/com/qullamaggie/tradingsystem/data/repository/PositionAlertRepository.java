package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.AlertStatus;
import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.PositionAlert;
import com.qullamaggie.tradingsystem.data.entity.PositionAlertType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionAlertRepository extends JpaRepository<PositionAlert, Long> {
    boolean existsByPositionAndTypeAndStatus(Position position, PositionAlertType type, AlertStatus status);
}
