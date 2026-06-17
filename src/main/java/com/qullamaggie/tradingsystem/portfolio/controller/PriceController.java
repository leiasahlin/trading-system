package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for retrieving daily price data.
 */
@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final DailyPriceRepository dailyPriceRepository;

    public PriceController(DailyPriceRepository dailyPriceRepository) {
        this.dailyPriceRepository = dailyPriceRepository;
    }

    /**
     * Returns all stored daily prices for the given stock symbol.
     *
     * @param symbol the ticker symbol (e.g. "AAPL")
     * @return list of daily prices
     */
    @GetMapping("/{symbol}")
    public List<DailyPrice> getPrices(@PathVariable String symbol) {
        return dailyPriceRepository.findByStockSymbol(symbol);
    }
}
