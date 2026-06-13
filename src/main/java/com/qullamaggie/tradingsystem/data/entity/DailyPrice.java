package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily OHLCV (Open, High, Low, Close, Volume) data for a stock.
 * Serves as the raw price data used to calculate technical indicators.
 * One row per stock per trading day.
 *
 * @author Leia Haglund Sahlin, leia.haglund05@gmail.com
 * @version v1.0, 2026-06-13
 */
@Entity
@Getter @Setter
@Table(name = "daily_prices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "date"}))
public class DailyPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume;

    @Column(nullable = false)
    private LocalDate date;
}
