package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Portfolio-level warning based directly on the broker's holdings, e.g. a
 * position exceeding the overnight exposure limit. Identified by ISIN rather
 * than linked to a Position, since broker holdings aren't matched to the
 * system's stocks yet.
 */
@Entity
@Getter @Setter
@Table(name = "portfolio_alerts")
public class PortfolioAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String isin;
    private String name;
    private BigDecimal weightPercent;
    private int suggestedSharesToSell;
    private LocalDate alertDate;

    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.NEW;

    private LocalDateTime createdAt = LocalDateTime.now();
}
