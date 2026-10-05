package com.qullamaggie.tradingsystem.market.controller;

import com.qullamaggie.tradingsystem.data.entity.MarketRegime;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current market regime with the reasoning behind it. */
@RestController
@RequestMapping("/api/market-regime")
public class MarketRegimeController {

    private final MarketRegimeService marketRegimeService;

    public MarketRegimeController(MarketRegimeService marketRegimeService) {
        this.marketRegimeService = marketRegimeService;
    }

    @GetMapping
    public ResponseEntity<MarketRegime> getLatest() {
        return marketRegimeService.getLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
