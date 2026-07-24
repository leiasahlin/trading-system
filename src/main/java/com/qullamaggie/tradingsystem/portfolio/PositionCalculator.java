package com.qullamaggie.tradingsystem.portfolio;

import com.qullamaggie.tradingsystem.data.entity.Position;
import com.qullamaggie.tradingsystem.data.entity.Transaction;
import com.qullamaggie.tradingsystem.data.entity.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

@Component
public class PositionCalculator {
    public PositionSummary calculateSummary(Position position, List<Transaction> transactions,
            BigDecimal currentPrice) {
        List<Transaction> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparing(Transaction::getExecutedAt));
        BigDecimal averagePrice = BigDecimal.ZERO;
        int remainingShares = 0;
        BigDecimal realizedPnL = BigDecimal.ZERO;

        for (Transaction transaction : sorted) {
            if (transaction.getType() == TransactionType.BUY) {
                BigDecimal existingCost = averagePrice.multiply(BigDecimal.valueOf(remainingShares));
                BigDecimal newCost = transaction.getPrice().multiply(BigDecimal.valueOf(transaction.getShares()));

                int newRemainingShares = remainingShares + transaction.getShares();

                averagePrice = existingCost.add(newCost)
                        .divide(BigDecimal.valueOf(newRemainingShares), 4, RoundingMode.HALF_UP);
                remainingShares = newRemainingShares;
            } else if (transaction.getType() == TransactionType.SELL) {
                BigDecimal saleProfit = transaction.getPrice().subtract(averagePrice)
                        .multiply(BigDecimal.valueOf(transaction.getShares()));

                realizedPnL = realizedPnL.add(saleProfit);
                remainingShares = remainingShares - transaction.getShares();
            }
        }

        BigDecimal unrealizedPnL = (currentPrice.subtract(averagePrice)).multiply(BigDecimal.valueOf(remainingShares));

        BigDecimal currentR = realizedPnL.add(unrealizedPnL)
                .divide(position.getInitialRisk(), 4, RoundingMode.HALF_UP);

        return new PositionSummary(averagePrice, currentPrice, remainingShares, realizedPnL, unrealizedPnL, currentR);
    }
}
