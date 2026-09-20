package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.service.PositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for retrieving position summaries.
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {
    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

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
}
