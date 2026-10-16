package com.qullamaggie.tradingsystem.data.repository;

import java.util.List;
import com.qullamaggie.tradingsystem.data.entity.AlertStatus;
import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.PositionAlert;
import com.qullamaggie.tradingsystem.data.entity.PositionAlertType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionAlertRepository extends JpaRepository<PositionAlert, Long> {
    boolean existsByPositionAndTypeAndStatus(Position position, PositionAlertType type, AlertStatus status);

    /** Position alerts not yet included in any notification email. */
    List<PositionAlert> findByNotifiedAtIsNull();

    /** E.g. every stop breach still marked NEW, to find positions left open after one. */
    List<PositionAlert> findByTypeAndStatus(PositionAlertType type, AlertStatus status);
}
