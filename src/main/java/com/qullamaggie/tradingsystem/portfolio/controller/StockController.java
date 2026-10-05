package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.entity.StockType;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.portfolio.StockAlreadyExistsException;
import com.qullamaggie.tradingsystem.portfolio.service.StockUniverseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for adding stocks to the tracked universe. Adding a stock
 * fetches its price history, calculates indicators and assesses eligibility.
 */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockUniverseService stockUniverseService;
    private final StockRepository stockRepository;

    public StockController(StockUniverseService stockUniverseService, StockRepository stockRepository) {
        this.stockUniverseService = stockUniverseService;
        this.stockRepository = stockRepository;
    }

    public record AddStockRequest(String symbol) {}

    public record AddStockResponse(String symbol, boolean eligible) {}

    @PostMapping
    public ResponseEntity<AddStockResponse> addStock(@RequestBody AddStockRequest request) {
        try {
            Stock stock = stockUniverseService.addStock(request.symbol());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AddStockResponse(stock.getSymbol(), stock.isEligible()));
        } catch (StockAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping
    public List<StockResponse> getAll() {
        return stockRepository.findAll().stream()
                .map(s -> new StockResponse(s.getSymbol(), s.getType(), s.isEligible(), s.getIsin()))
                .toList();
    }

    public record StockResponse(String symbol, StockType type, boolean eligible, String isin) {}
}
