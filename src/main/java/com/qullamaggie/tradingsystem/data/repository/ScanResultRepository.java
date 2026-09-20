package com.qullamaggie.tradingsystem.data.repository;

import com.qullamaggie.tradingsystem.data.entity.ScanResult;
import com.qullamaggie.tradingsystem.data.entity.SetupType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {
    List<ScanResult> findBySetupType (SetupType setupType);
    List<ScanResult> findBySetupTypeAndCreatedAtAfter(SetupType setupType, LocalDateTime since);
}
