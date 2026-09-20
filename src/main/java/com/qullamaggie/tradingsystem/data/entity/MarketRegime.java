package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_regimes")
@Getter
@Setter
public class MarketRegime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;

    @Enumerated(EnumType.STRING)
    private RegimeStatus status;

    /** Human-readable breakdown per index, for transparency in the frontend. */
    @Column(columnDefinition = "TEXT")
    private String details;

    private LocalDateTime createdAt;
}
