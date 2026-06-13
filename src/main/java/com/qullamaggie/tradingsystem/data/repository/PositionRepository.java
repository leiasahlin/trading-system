package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {
}
