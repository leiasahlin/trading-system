package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "positions")
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "entry_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 12, scale = 4)
    private BigDecimal exitPrice;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal shares;

    @Column(name = "stop_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal stopPrice;

    @Column(name = "risk_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "setup_type", nullable = false, length = 20)
    private SetupType setupType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionStatus status = PositionStatus.OPEN;

    @Column(precision = 12, scale = 4)
    private BigDecimal pnl;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
