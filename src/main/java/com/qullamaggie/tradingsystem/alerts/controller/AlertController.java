package com.qullamaggie.tradingsystem.alerts.controller;

import com.qullamaggie.tradingsystem.data.entity.Alert;
import com.qullamaggie.tradingsystem.data.entity.AlertStatus;
import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.portfolio.AlertNotFoundException;
import com.qullamaggie.tradingsystem.portfolio.service.AlertExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only view of generated buy/short alerts. */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;
    private final AlertExecutionService alertExecutionService;

    public AlertController(AlertRepository alertRepository, AlertExecutionService alertExecutionService) {
        this.alertRepository = alertRepository;
        this.alertExecutionService = alertExecutionService;
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

    public record ExecuteRequest(Integer shares, BigDecimal fillPrice) {}

    @PostMapping("/{id}/execute")
    public ResponseEntity<Long> execute(@PathVariable Long id, @RequestBody(required = false) ExecuteRequest request) {
        Position position = alertExecutionService.execute(id,
                request == null ? null : request.shares(),
                request == null ? null : request.fillPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(position.getId());
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long id) {
        alertExecutionService.dismiss(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(AlertNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleAlreadyHandled(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}