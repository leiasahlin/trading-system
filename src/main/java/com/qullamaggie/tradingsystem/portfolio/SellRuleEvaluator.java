package com.qullamaggie.tradingsystem.portfolio;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Evaluates the Kullamägi sell rules against an open position's transaction
 * history. Each method answers a yes/no question about whether a rule has
 * triggered - it never acts on the position itself.
 */
@Component
public class SellRuleEvaluator {
    private SellRuleConfig config;

    public SellRuleEvaluator(SellRuleConfig config) {
        this.config = config;
    }

    /**
     * Checks whether the stop should be moved to breakeven
     */
    public boolean shouldMoveStopToBreakeven(Position position, List<Transaction> transactions) {
        boolean isStopMoved = position.getStopPrice().compareTo(position.getInitialStopPrice()) != 0;
        return hasPartialSell(transactions) && !isStopMoved;
    }

    public boolean shouldTrim(Position position, List<Transaction> transactions,
                              PositionSummary summary, LocalDate asOfDate) {
        if (hasPartialSell(transactions)) {
            return false;
        }

        long daysHeld = ChronoUnit.DAYS.between(position.getOpenedAt().toLocalDate(), asOfDate);

        boolean hasReachedMinTrimR = summary.currentR().compareTo(config.minTrimR()) >= 0;
        boolean withinDayWindow = daysHeld >= config.minDaysHeld() && daysHeld <= config.maxDaysHeld();

        return hasReachedMinTrimR || withinDayWindow;
    }

    // public boolean shouldUpdateTrailingStop(Position position, BigDecimal maValue, BigDecimal latestClose) {}

    // public boolean shouldExit(Position position, BigDecimal maValue, BigDecimal latestClose) {}

    private boolean hasPartialSell(List<Transaction> transactions) {
        return transactions.stream()
                .anyMatch(transaction -> transaction.getType() == TransactionType.SELL);
    }

}
