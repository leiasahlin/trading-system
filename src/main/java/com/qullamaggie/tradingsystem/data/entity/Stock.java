package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
@Getter @Setter
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String exchange;

    private String sector;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false,updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
