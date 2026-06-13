package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "scan_results")
public class ScanResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "setup_type", nullable = false, length = 20)
    private SetupType setupType;

    @Column(name = "entry_price", precision = 12, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "stop_price", precision = 12, scale = 4)
    private BigDecimal stopPrice;

    @Column(precision = 8, scale = 4)
    private BigDecimal adr;

    @Column(precision = 4, scale = 2)
    private BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
