package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.AlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import com.qullamaggie.tradingsystem.data.repository.TransactionRepository;
import com.qullamaggie.tradingsystem.portfolio.AlertNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Turns an acted-on alert into a tracked position. The alert already holds the
 * setup, entry and stop, so only the actual fill needs supplying - and only
 * once the order has gone through, since share count feeds the R calculation.
 */
@Service
public class AlertExecutionService {

    private final AlertRepository alertRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;

    public AlertExecutionService(AlertRepository alertRepository,
                                 PositionRepository positionRepository,
                                 TransactionRepository transactionRepository) {
        this.alertRepository = alertRepository;
        this.transactionRepository = transactionRepository;
        this.positionRepository = positionRepository;
    }

    /**
     * @param shares     actual fill, or null to use the alert's suggestion
     * @param fillPrice  actual fill price, or null to use the alert's entry
     */
    @Transactional
    public Position execute(Long alertId, Integer shares, BigDecimal fillPrice) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        if (alert.getStatus() != AlertStatus.NEW) {
            throw new IllegalStateException("Alert " + alertId + " är redan hanterad");
        }

        int actualShares = shares != null ? shares : alert.getShares();
        BigDecimal actualPrice = fillPrice != null ? fillPrice : alert.getEntryPrice();
        BigDecimal stop = alert.getStopPrice();

        Position position = new Position();
        position.setStock(alert.getStock());
        position.setSetupType(setupTypeOf(alert));
        position.setStopPrice(stop);
        // Referenspunkt för hela livscykeln: breakeven- och trailing-reglerna
        // upptäcker att stoppen flyttats genom att jämföra mot den här
        position.setInitialStopPrice(stop);
        position.setInitialRisk(actualPrice.subtract(stop).abs()
                .multiply(BigDecimal.valueOf(actualShares)));
        position.setRiskPercent(BigDecimal.ZERO);   // se kommentaren nedan
        positionRepository.save(position);

        Transaction buy = new Transaction();
        buy.setPosition(position);
        buy.setType(TransactionType.BUY);
        buy.setShares(actualShares);
        buy.setPrice(actualPrice);
        transactionRepository.save(buy);

        alert.setStatus(AlertStatus.EXECUTED);
        alertRepository.save(alert);

        return position;
    }

    @Transactional
    public void dismiss(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
        alert.setStatus(AlertStatus.DISMISSED);
        alertRepository.save(alert);
    }

    /** The alert's type string carries the setup; map it back to the enum. */
    private SetupType setupTypeOf(Alert alert) {
        String type = alert.getType();
        if (type.startsWith("PARABOLIC_SHORT")) {
            return SetupType.PARABOLIC_SHORT;
        }
        return type.startsWith("EPISODIC_PIVOT") ? SetupType.EPISODIC_PIVOT : SetupType.BREAKOUT;
    }
}