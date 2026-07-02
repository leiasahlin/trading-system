package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * Calculated technical indicators for a stock on a given date,
 * derived from historical DailyPrice data. Includes moving averages
 * (MA10/20/50), average daily range (ADR20), average volume, and
 * prior price movement - used by the scanner to identify setups.
 *
 * @author Leia Haglund Sahlin, leia.haglund05@gmail.com
 * @version v1.0, 2026-06-13
 */
@Entity
@Getter @Setter
@Table(name = "indicators",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "date"}))
public class Indicator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "ma_10", precision = 12, scale = 4)
    private BigDecimal ma10;

    @Column(name = "ma_20", precision = 12, scale = 4)
    private BigDecimal ma20;

    @Column(name = "ma_50", precision = 12, scale = 4)
    private BigDecimal ma50;

    @Column(name = "adr_20", precision = 8, scale = 4)
    private BigDecimal adr20;

    @Column(name = "atr_20", precision = 8, scale = 4)
    private BigDecimal atr20;

    @Column(name = "volume_avg_20")
    private Long volumeAvg20;

    @Column(name = "prior_move", precision = 8, scale = 4)
    private BigDecimal priorMove;

    @Column(name = "consolidation_range", precision = 8, scale = 4)
    private BigDecimal consolidationRange;
}
