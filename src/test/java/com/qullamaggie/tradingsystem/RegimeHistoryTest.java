package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.backtest.RegimeHistory;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.RegimeStatus;
import com.qullamaggie.tradingsystem.indicators.IndicatorCalculator;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.MarketRegimeEvaluator;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RegimeHistoryTest {

    private static final LocalDate START = LocalDate.of(2020, 1, 1);

    private RegimeHistory regimeHistory;

    @BeforeEach
    void setUp() {
        MarketRegimeConfig config = new MarketRegimeConfig(List.of("SPY"), RegimeMode.ANY, 10, 20);
        regimeHistory = new RegimeHistory(
                new IndicatorCalculator(), new MarketRegimeEvaluator(config), config);
    }

    @Test
    void risingMarketShouldBeRiskOn() {
        List<DailyPrice> prices = pricesWithStep(30, 100, 1);   // 100, 101, 102 ...

        Map<LocalDate, RegimeStatus> regimes = regimeHistory.build("SPY", prices);

        // MA10 of the latest closes is above MA20 when prices rise
        assertEquals(RegimeStatus.RISK_ON, regimes.get(prices.getLast().getDate()));
    }

    @Test
    void fallingMarketShouldBeRiskOff() {
        List<DailyPrice> prices = pricesWithStep(30, 200, -1);  // 200, 199, 198 ...

        Map<LocalDate, RegimeStatus> regimes = regimeHistory.build("SPY", prices);

        assertEquals(RegimeStatus.RISK_OFF, regimes.get(prices.getLast().getDate()));
    }

    @Test
    void firstDayShouldBeTheFirstWithEnoughHistoryForSlowMa() {
        List<DailyPrice> prices = pricesWithStep(30, 100, 1);

        Map<LocalDate, RegimeStatus> regimes = regimeHistory.build("SPY", prices);

        // Position 19 is the 20th day: the first one MA20 can be computed for
        assertTrue(regimes.containsKey(prices.get(19).getDate()));
        assertFalse(regimes.containsKey(prices.get(18).getDate()));
        assertEquals(30 - 19, regimes.size());
    }

    @Test
    void shouldFlipToRiskOffWhenTrendTurns() {
        // 30 days up, then 30 days down
        List<DailyPrice> prices = new ArrayList<>(pricesWithStep(30, 100, 1));
        BigDecimal top = prices.getLast().getClose();
        for (int i = 1; i <= 30; i++) {
            prices.add(price(START.plusDays(29 + i), top.subtract(BigDecimal.valueOf(i))));
        }

        Map<LocalDate, RegimeStatus> regimes = regimeHistory.build("SPY", prices);

        assertEquals(RegimeStatus.RISK_ON, regimes.get(prices.get(29).getDate()));
        assertEquals(RegimeStatus.RISK_OFF, regimes.get(prices.getLast().getDate()));
    }

    @Test
    void regimeOnADayShouldNotDependOnLaterPrices() {
        // Same first 40 days in both lists, but the long list then crashes
        List<DailyPrice> shortHistory = pricesWithStep(40, 100, 1);
        List<DailyPrice> longHistory = new ArrayList<>(shortHistory);
        for (int i = 1; i <= 20; i++) {
            longHistory.add(price(START.plusDays(39 + i), BigDecimal.valueOf(50)));
        }

        Map<LocalDate, RegimeStatus> fromShort = regimeHistory.build("SPY", shortHistory);
        Map<LocalDate, RegimeStatus> fromLong = regimeHistory.build("SPY", longHistory);

        // The crash after day 40 must not change how day 1-40 were judged (no lookahead)
        for (DailyPrice p : shortHistory) {
            assertEquals(fromShort.get(p.getDate()), fromLong.get(p.getDate()),
                    "Regime changed for " + p.getDate());
        }
    }

    @Test
    void tooShortHistoryShouldGiveNoRegimes() {
        List<DailyPrice> prices = pricesWithStep(19, 100, 1);

        Map<LocalDate, RegimeStatus> regimes = regimeHistory.build("SPY", prices);

        assertTrue(regimes.isEmpty());
    }

    /** Consecutive days, oldest first, with the close changing by step each day. */
    private List<DailyPrice> pricesWithStep(int count, int firstClose, int step) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(price(START.plusDays(i), BigDecimal.valueOf(firstClose + (long) i * step)));
        }
        return prices;
    }

    private DailyPrice price(LocalDate date, BigDecimal close) {
        DailyPrice p = new DailyPrice();
        p.setDate(date);
        p.setClose(close);
        return p;
    }
}