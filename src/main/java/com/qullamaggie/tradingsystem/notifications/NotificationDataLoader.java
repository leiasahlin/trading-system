package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import com.qullamaggie.tradingsystem.portfolio.service.PositionSummaryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns stored alerts into the plain data the emails are built from. Shared
 * by the evening email and the urgent emails, so a stop breach looks the
 * same in both.
 */
@Component
public class NotificationDataLoader {

    private static final Logger log = LoggerFactory.getLogger(NotificationDataLoader.class);

    private final IndicatorRepository indicatorRepository;
    private final StockRepository stockRepository;
    private final PositionSummaryProvider positionSummaryProvider;
    private final MarketRegimeConfig marketRegimeConfig;
    private final AccountConfig accountConfig;
    private final SellRuleConfig sellRuleConfig;

    public NotificationDataLoader(IndicatorRepository indicatorRepository,
                                  StockRepository stockRepository,
                                  PositionSummaryProvider positionSummaryProvider,
                                  MarketRegimeConfig marketRegimeConfig,
                                  AccountConfig accountConfig,
                                  SellRuleConfig sellRuleConfig) {
        this.indicatorRepository = indicatorRepository;
        this.stockRepository = stockRepository;
        this.positionSummaryProvider = positionSummaryProvider;
        this.marketRegimeConfig = marketRegimeConfig;
        this.accountConfig = accountConfig;
        this.sellRuleConfig = sellRuleConfig;
    }

    public AccountConfig account() {
        return accountConfig;
    }

    public SellRuleConfig sellRules() {
        return sellRuleConfig;
    }

    public MarketRegimeConfig marketRegime() {
        return marketRegimeConfig;
    }

    public BuySignal toBuySignal(Alert alert) {
        BigDecimal adr = latestIndicator(alert.getStock()).map(Indicator::getAdr20).orElse(null);
        return new BuySignal(alert.getId(), alert.getStock().getSymbol(), setupOf(alert.getType()),
                alert.getEntryPrice(), alert.getStopPrice(), alert.getShares(), alert.getRiskPercent(), adr);
    }

    public PositionWarning toPositionWarning(PositionAlert alert, LocalDate today) {
        Position position = alert.getPosition();
        BigDecimal maValue = latestIndicator(position.getStock())
                .map(indicator -> sellRuleConfig.maPeriod() == 20 ? indicator.getMa20() : indicator.getMa10())
                .orElse(null);
        return new PositionWarning(position.getId(), position.getStock().getSymbol(), position.getSetupType(),
                alert.getType(), alert.getSuggestedStopPrice(), position.getStopPrice(), position.getInitialRisk(),
                ChronoUnit.DAYS.between(position.getOpenedAt().toLocalDate(), today),
                summaryOrNull(position), maValue, alert.getTriggerPrice());
    }

    public OvernightWarning toOvernightWarning(PortfolioAlert alert) {
        return new OvernightWarning(alert.getName(), alert.getWeightPercent(), alert.getSuggestedSharesToSell());
    }

    /** The same readings MarketRegimeService judged the regime on, for the explanation. */
    public List<IndexReading> indexReadings() {
        int fast = marketRegimeConfig.maFastPeriod();
        int slow = marketRegimeConfig.maSlowPeriod();
        List<IndexReading> readings = new ArrayList<>();
        for (String symbol : marketRegimeConfig.indexes()) {
            Optional<Indicator> indicator = stockRepository.findBySymbol(symbol).flatMap(this::latestIndicator);
            readings.add(new IndexReading(symbol,
                    fast, indicator.map(i -> maForPeriod(i, fast)).orElse(null),
                    slow, indicator.map(i -> maForPeriod(i, slow)).orElse(null)));
        }
        return readings;
    }

    /** Alert types carry the setup as a prefix, e.g. BREAKOUT_BUY or EPISODIC_PIVOT_BUY. */
    public static SetupType setupOf(String alertType) {
        if (alertType.startsWith("PARABOLIC_SHORT")) {
            return SetupType.PARABOLIC_SHORT;
        }
        if (alertType.startsWith("EPISODIC_PIVOT")) {
            return SetupType.EPISODIC_PIVOT;
        }
        return SetupType.BREAKOUT;
    }

    /** A missing price shouldn't stop an email - the warning is shown without the numbers. */
    private PositionSummary summaryOrNull(Position position) {
        try {
            return positionSummaryProvider.buildSummary(position);
        } catch (RuntimeException e) {
            log.warn("Kunde inte räkna sammanfattning för {}: {}", position.getStock().getSymbol(), e.getMessage());
            return null;
        }
    }

    private Optional<Indicator> latestIndicator(Stock stock) {
        return indicatorRepository.findTop1ByStockOrderByDateDesc(stock);
    }

    private static BigDecimal maForPeriod(Indicator indicator, int period) {
        return switch (period) {
            case 10 -> indicator.getMa10();
            case 20 -> indicator.getMa20();
            case 50 -> indicator.getMa50();
            default -> null;
        };
    }
}
