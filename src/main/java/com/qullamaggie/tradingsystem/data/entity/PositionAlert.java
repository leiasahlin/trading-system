package com.qullamaggie.tradingsystem.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "position_alerts")
public class PositionAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Position position;

    @Enumerated(EnumType.STRING)
    private PositionAlertType type;

    private BigDecimal suggestedStopPrice;

    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    private LocalDateTime createdAt;

    /** The price that triggered the alert, e.g. the 5-minute close below the stop; null if not recorded. */
    @Column(name = "trigger_price", precision = 12, scale = 4)
    private BigDecimal triggerPrice;

    /** When this was included in a notification email; null = not sent yet. */
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;
}
