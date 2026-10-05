package com.qullamaggie.tradingsystem.alerts.controller;

import com.qullamaggie.tradingsystem.data.entity.Alert;
import com.qullamaggie.tradingsystem.data.entity.AlertStatus;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only view of generated buy/short alerts. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public record AlertResponse(Long id, String symbol, String type, BigDecimal entryPrice,
                                BigDecimal stopPrice, int shares, String message,
                                AlertStatus status, LocalDateTime createdAt) {}

    @GetMapping
    public List<AlertResponse> getNewAlerts() {
        return alertRepository.findByStatus(AlertStatus.NEW).stream().map(this::toResponse).toList();
    }

    @GetMapping("/all")
    public List<AlertResponse> getAllAlerts() {
        return alertRepository.findAll().stream().map(this::toResponse).toList();
    }

    private AlertResponse toResponse(Alert alert) {
        return new AlertResponse(alert.getId(), alert.getStock().getSymbol(), alert.getType(),
                alert.getEntryPrice(), alert.getStopPrice(), alert.getShares(),
                alert.getMessage(), alert.getStatus(), alert.getCreatedAt());
    }
}