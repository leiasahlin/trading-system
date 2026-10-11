package com.qullamaggie.tradingsystem.portfolio;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives a position's state from its transactions. Direction-aware:
 * for a long the opening side is BUY, for a short it is SELL (see
 * SetupType.openingSide). Transactions on the opening side add to the
 * position and move the average price; the opposite side reduces it and
 * realizes PnL against the average.
 */
@Component
public class PositionCalculator {
    public PositionSummary calculateSummary(Position position, List<Transaction> transactions,
            BigDecimal currentPrice) {
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparing(Transaction::getExecutedAt));

        TransactionType openingSide = position.getSetupType().openingSide();
        // +1 för long, -1 för short: vinsten är (pris - snitt) för long, (snitt - pris) för short
        BigDecimal direction = position.getSetupType().isShort() ? BigDecimal.ONE.negate() : BigDecimal.ONE;

        BigDecimal averagePrice = BigDecimal.ZERO;
        int remainingShares = 0;
        BigDecimal realizedPnL = BigDecimal.ZERO;
        boolean flat = false;

        for (Transaction transaction : sorted) {
            if (flat) {
                throw new IllegalArgumentException(
                        "Positionen blir stängd före transaktionen " + transaction.getExecutedAt());
            }

            if (transaction.getType() == openingSide) {
                BigDecimal existingCost = averagePrice.multiply(BigDecimal.valueOf(remainingShares));
                BigDecimal newCost = transaction.getPrice().multiply(BigDecimal.valueOf(transaction.getShares()));

                int newRemainingShares = remainingShares + transaction.getShares();

                averagePrice = existingCost.add(newCost)
                        .divide(BigDecimal.valueOf(newRemainingShares), 4, RoundingMode.HALF_UP);
                remainingShares = newRemainingShares;
            } else {
                if (transaction.getShares() > remainingShares) {
                    throw new IllegalArgumentException("Kan inte minska med " + transaction.getShares()
                            + " aktier, bara " + remainingShares + " kvar vid " + transaction.getExecutedAt());
                }
                BigDecimal saleProfit = transaction.getPrice().subtract(averagePrice)
                        .multiply(direction)
                        .multiply(BigDecimal.valueOf(transaction.getShares()));

                realizedPnL = realizedPnL.add(saleProfit);
                remainingShares = remainingShares - transaction.getShares();
                flat = remainingShares == 0;
            }
        }

        BigDecimal unrealizedPnL = currentPrice.subtract(averagePrice)
                .multiply(direction)
                .multiply(BigDecimal.valueOf(remainingShares));

        BigDecimal currentR = realizedPnL.add(unrealizedPnL)
                .divide(position.getInitialRisk(), 4, RoundingMode.HALF_UP);

        return new PositionSummary(averagePrice, currentPrice, remainingShares, realizedPnL, unrealizedPnL, currentR);
    }
}
