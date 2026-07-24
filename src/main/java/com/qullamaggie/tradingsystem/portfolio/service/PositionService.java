package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.portfolio.PositionCalculator;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final DailyPriceRepository dailyPriceRepository; // eller DailyPriceRepository - se nedan
    private final PositionCalculator positionCalculator;

    public PositionService(PositionRepository positionRepository,
                           TransactionRepository transactionRepository,
                           IndicatorRepository indicatorRepository, DailyPriceRepository dailyPriceRepository,
                           PositionCalculator positionCalculator) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.dailyPriceRepository = dailyPriceRepository;
        this.positionCalculator = positionCalculator;
    }

    public Optional<PositionSummary> getSummary(Long positionId) {
        return positionRepository.findById(positionId)
                .map(this::buildSummary);
    }

    public List<PositionSummary> getSummariesForAllOpenPositions() {
        List<Position> openPositions = positionRepository.findByStatus(PositionStatus.OPEN);
        return openPositions.stream()
                .map(this::buildSummary)
                .toList();
    }

    private PositionSummary buildSummary(Position position) {
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
