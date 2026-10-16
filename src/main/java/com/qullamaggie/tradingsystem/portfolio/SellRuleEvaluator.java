package com.qullamaggie.tradingsystem.portfolio;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates the Kullamägi sell rules against an open position's transaction
 * history. Each method answers a yes/no question about whether a rule has
 * triggered - it never acts on the position itself.
 */
@Component
public class SellRuleEvaluator {
    private final SellRuleConfig config;

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

    /**
     * Calculates the new trailing stop price, if one should be applied.
     * Returns empty if the position hasn't reached breakeven yet, or if
     * the MA value wouldn't move the stop upward (a trailing stop must
     * never move down).
     */
    public Optional<BigDecimal> calculateNewTrailingStop(Position position, BigDecimal maValue) {
        if (position.getStopPrice().compareTo(position.getInitialStopPrice()) == 0) {
            return  Optional.empty();
        }

        if (maValue.compareTo(position.getStopPrice()) <= 0) {
            return Optional.empty();
        }

        return Optional.of(maValue);
    }

    /**
     * True if today's close has fallen below the trailing MA line after the
     * position has been trimmed - signals that the remainder should be sold.
     * Before the trim, the initial stop protects the position, not the MA.
     */
    public boolean shouldExit(List<Transaction> transactions, BigDecimal maValue, BigDecimal latestClose) {
        // MA-regeln gäller bara resten efter avsälj - innan dess är det den
        // ursprungliga stoppen som skyddar positionen
        return hasPartialSell(transactions) && latestClose.compareTo(maValue) < 0;
    }

    private boolean hasPartialSell(List<Transaction> transactions) {
        return transactions.stream()
                .anyMatch(transaction -> transaction.getType() == TransactionType.SELL);
    }

}
