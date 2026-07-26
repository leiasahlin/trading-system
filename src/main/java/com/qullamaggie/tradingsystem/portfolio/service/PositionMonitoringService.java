package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import com.qullamaggie.tradingsystem.portfolio.SellRuleEvaluator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PositionMonitoringService {
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final IndicatorRepository indicatorRepository;
    private final PositionAlertRepository positionAlertRepository;
    private final SellRuleEvaluator sellRuleEvaluator;
    private final PositionSummaryProvider positionSummaryProvider;
    private final SellRuleConfig sellRuleConfig;

    public PositionMonitoringService(PositionRepository positionRepository, TransactionRepository transactionRepository,
                                     IndicatorRepository indicatorRepository, PositionAlertRepository positionAlertRepository,
                                     SellRuleEvaluator sellRuleEvaluator, PositionSummaryProvider positionSummaryProvider, SellRuleConfig sellRuleConfig) {
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.indicatorRepository = indicatorRepository;
        this.positionAlertRepository = positionAlertRepository;
        this.sellRuleEvaluator = sellRuleEvaluator;
        this.positionSummaryProvider = positionSummaryProvider;
        this.sellRuleConfig = sellRuleConfig;
    }

    public void monitorAllOpenPositions() {
        List<Position> openPositions = positionRepository.findByStatus(PositionStatus.OPEN);
        for (Position position : openPositions) {
            monitorPosition(position);
        }
    }

    private void monitorPosition(Position position) {
        List<Transaction> transactions = transactionRepository.findByPositionOrderByExecutedAtAsc(position);
        PositionSummary summary = positionSummaryProvider.buildSummary(position);
        BigDecimal maValue = getMaValue(position);

        if (sellRuleEvaluator.shouldMoveStopToBreakeven(position, transactions)) {
            position.setStopPrice(summary.averagePrice());
            positionRepository.save(position);
            createAlertIfNotExists(position, PositionAlertType.MOVE_STOP_TO_BREAKEVEN, position.getStopPrice());
        }

        if (sellRuleEvaluator.shouldTrim(position, transactions, summary, LocalDate.now())) {
            createAlertIfNotExists(position, PositionAlertType.TRIM, null);
        }

        // TODO 3: sellRuleEvaluator.calculateNewTrailingStop(position, maValue)
        //         Optional<BigDecimal> - om den innehåller ett värde:
        //           - position.setStopPrice(nyaVärdet)
        //           - positionRepository.save(position)
        //           - createAlertIfNotExists(position, PositionAlertType.TRAILING_STOP_UPDATE, nyaVärdet)
        Optional<BigDecimal> trailingStop = sellRuleEvaluator.calculateNewTrailingStop(position, maValue);

        if (trailingStop.isPresent()) {
            BigDecimal newStopPrice = trailingStop.get();
            position.setStopPrice(newStopPrice);
            positionRepository.save(position);
            createAlertIfNotExists(position, PositionAlertType.TRAILING_STOP_UPDATE, newStopPrice);
        }


        // TODO 4: sellRuleEvaluator.shouldExit(maValue, summary.currentPrice())
        //         Om true: createAlertIfNotExists(position, PositionAlertType.EXIT, null)
        if (sellRuleEvaluator.shouldExit(maValue, summary.currentPrice())) {
            createAlertIfNotExists(position, PositionAlertType.EXIT, null);
        }

    }

    private BigDecimal getMaValue(Position position) {
        Indicator indicator = indicatorRepository.findTop1ByStockOrderByDateDesc(position.getStock())
                .orElseThrow(() -> new IllegalStateException(
                        "Ingen indikatordata tillgänglig för " + position.getStock().getSymbol()));

        return sellRuleConfig.maPeriod() == 20 ? indicator.getMa20() : indicator.getMa10();
    }

    private void createAlertIfNotExists(Position position, PositionAlertType type, BigDecimal suggestedStopPrice) {
        if (!positionAlertRepository.existsByPositionAndTypeAndStatus(position, type, AlertStatus.NEW)) {
            PositionAlert alert = new PositionAlert();
            alert.setPosition(position);
            alert.setType(type);
            alert.setSuggestedStopPrice(suggestedStopPrice);
            alert.setStatus(AlertStatus.NEW);
            alert.setCreatedAt(LocalDateTime.now());
            positionAlertRepository.save(alert);
        }
    }
}
