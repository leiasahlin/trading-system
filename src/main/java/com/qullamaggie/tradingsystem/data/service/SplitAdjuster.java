package com.qullamaggie.tradingsystem.data.service;

import com.qullamaggie.tradingsystem.data.dto.StockSplit;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Detects and corrects unadjusted split history. The provider delivers raw
 * prices, so a reverse split leaves an artificial jump that would otherwise be
 * read as an enormous price move.
 */
@Component
public class SplitAdjuster {

    /** A day-over-day change this large is a split rather than a real move. */
    private static final BigDecimal SUSPECT_CHANGE_FACTOR = BigDecimal.valueOf(2);

    /**
     * True if any two consecutive closes differ by more than the suspect factor
     * in either direction. Cheap pre-check so split data is only fetched for the
     * few stocks that need it.
     *
     * @param prices oldest first
     */
    public boolean looksLikeSplit(List<DailyPrice> prices) {
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal previous = prices.get(i - 1).getClose();
            BigDecimal current = prices.get(i).getClose();
            if (previous == null || current == null || previous.signum() == 0) {
                continue;
            }
            BigDecimal factor = current.divide(previous, 4, RoundingMode.HALF_UP);
            if (factor.compareTo(SUSPECT_CHANGE_FACTOR) > 0
                    || factor.compareTo(BigDecimal.ONE.divide(SUSPECT_CHANGE_FACTOR, 4, RoundingMode.HALF_UP)) < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies each split to the prices dated before it: prices are multiplied by
     * the ratio and volumes divided, bringing the history onto today's scale.
     */
    public void adjust(List<DailyPrice> prices, List<StockSplit> splits) {
        for (StockSplit split : splits) {
            for (DailyPrice price : prices) {
                if (price.getDate().isBefore(split.date())) {
                    price.setOpen(scale(price.getOpen(), split.ratio()));
                    price.setHigh(scale(price.getHigh(), split.ratio()));
                    price.setLow(scale(price.getLow(), split.ratio()));
                    price.setClose(scale(price.getClose(), split.ratio()));
                    price.setVolume(BigDecimal.valueOf(price.getVolume())
                            .divide(split.ratio(), 0, RoundingMode.HALF_UP).longValue());
                }
            }
        }
    }

    private BigDecimal scale(BigDecimal value, BigDecimal ratio) {
        return value.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
    }
}