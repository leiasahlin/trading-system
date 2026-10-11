package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import com.qullamaggie.tradingsystem.portfolio.PositionClosedException;
import com.qullamaggie.tradingsystem.portfolio.PositionNotFoundException;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.service.PositionService;
import com.qullamaggie.tradingsystem.portfolio.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for position summaries and for registering trims,
 * exits and adds against a position.
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {
    private final PositionService positionService;
    private final TransactionService transactionService;

    public PositionController(PositionService positionService, TransactionService transactionService) {
        this.positionService = positionService;
        this.transactionService = transactionService;
    }

    /**
     * @param type       BUY or SELL - the actual order side
     * @param executedAt optional, defaults to now (ISO, e.g. 2026-10-08T15:30:00)
     */
    public record TransactionRequest(TransactionType type, Integer shares, BigDecimal price,
                                     LocalDateTime executedAt) {}

    /**
     * Returns the derived summary (average price, realized/unrealized PnL, current R)
     * for a single position.
     *
     * @param id the position's database id
     * @return 200 with the summary, or 404 if no such position exists
     */
    @GetMapping("/{id}")
    public ResponseEntity<PositionSummary> getSummary(@PathVariable Long id) {
        return positionService.getSummary(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns summaries for all currently open positions.
     */
    @GetMapping
    public ResponseEntity<List<PositionSummary>> getAllOpenSummaries() {
        return ResponseEntity.ok(positionService.getSummariesForAllOpenPositions());
    }

    /**
     * Registers a trim, exit or add. The position closes automatically when
     * the last share is sold.
     *
     * @return 201 with the updated summary, 400 if the transaction is invalid,
     *         404 if no such position exists, 409 if the position is closed
     */
    @PostMapping("/{id}/transactions")
    public ResponseEntity<PositionSummary> recordTransaction(@PathVariable Long id,
                                                             @RequestBody TransactionRequest request) {
        transactionService.record(id, request.type(), request.shares(), request.price(), request.executedAt());
        return positionService.getSummary(id)
                .map(summary -> ResponseEntity.status(HttpStatus.CREATED).body(summary))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(PositionNotFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(PositionClosedException.class)
    public ResponseEntity<String> handleClosed(PositionClosedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
