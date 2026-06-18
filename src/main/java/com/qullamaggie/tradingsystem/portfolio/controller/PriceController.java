package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.data.dto.DailyPriceDto;
import com.qullamaggie.tradingsystem.data.dto.DailyPriceMapper;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for retrieving daily price data.
 */
@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final DailyPriceMapper dailyPriceMapper;
    private final DailyPriceRepository dailyPriceRepository;

    public PriceController(DailyPriceRepository dailyPriceRepository,
                           DailyPriceMapper dailyPriceMapper) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.dailyPriceMapper = dailyPriceMapper;
    }

    /**
     * Returns all stored daily prices for the given stock symbol.
     *
     * @param symbol the ticker symbol (e.g. "AAPL")
     * @return list of daily prices
     */
    @GetMapping("/{symbol}")
    public List<DailyPriceDto> getPrices(@PathVariable String symbol) {
        List<DailyPrice> prices = dailyPriceRepository.findByStockSymbol(symbol);

        List<DailyPriceDto> dtos = new ArrayList<>();
        for (DailyPrice price : prices) {
            dtos.add(dailyPriceMapper.toDto(price));
        }
        return dtos;
    }
}
