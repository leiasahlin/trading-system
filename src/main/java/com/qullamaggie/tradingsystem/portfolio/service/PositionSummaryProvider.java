package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.TransactionRepository;
import com.qullamaggie.tradingsystem.portfolio.PositionCalculator;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PositionSummaryProvider {

    private final TransactionRepository transactionRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final PositionCalculator positionCalculator;

    public PositionSummaryProvider(TransactionRepository transactionRepository, DailyPriceRepository dailyPriceRepository, PositionCalculator positionCalculator) {
        this.transactionRepository = transactionRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.positionCalculator = positionCalculator;
    }

    public PositionSummary buildSummary(Position position) {
        List<Transaction> transactions = transactionRepository.findByPositionOrderByExecutedAtAsc(position);
        BigDecimal currentPrice = getLatestClosePrice(position.getStock());
        return positionCalculator.calculateSummary(position, transactions, currentPrice);
    }

    private BigDecimal getLatestClosePrice(Stock stock) {
        return dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)
                .map(DailyPrice::getClose)
                .orElseThrow(() -> new IllegalStateException(
                        "Ingen prisdata tillgänglig för " + stock.getSymbol()));
    }
}
