package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import com.qullamaggie.tradingsystem.notifications.EveningDigest.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import com.qullamaggie.tradingsystem.portfolio.service.PositionSummaryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Sends the evening summary after the pipeline: every alert not yet
 * notified, plus the market regime and the numbers behind each suggestion.
 *
 * An alert counts as notified only once the email has actually gone out.
 * If sending fails, notifiedAt stays null and the alert is picked up again
 * by the next email - nothing is lost to a network hiccup.
 */
@Service
public class EveningDigestService {

    private static final Logger log = LoggerFactory.getLogger(EveningDigestService.class);

    private final AlertRepository alertRepository;
    private final PositionAlertRepository positionAlertRepository;
    private final PortfolioAlertRepository portfolioAlertRepository;
    private final IndicatorRepository indicatorRepository;
    private final StockRepository stockRepository;
    private final PositionSummaryProvider positionSummaryProvider;
    private final MarketRegimeService marketRegimeService;
    private final MarketRegimeConfig marketRegimeConfig;
    private final AccountConfig accountConfig;
    private final SellRuleConfig sellRuleConfig;
    private final EveningDigestFormatter formatter;
    private final NotificationService notificationService;

    public EveningDigestService(AlertRepository alertRepository,
                                PositionAlertRepository positionAlertRepository,
                                PortfolioAlertRepository portfolioAlertRepository,
                                IndicatorRepository indicatorRepository,
                                StockRepository stockRepository,
                                PositionSummaryProvider positionSummaryProvider,
                                MarketRegimeService marketRegimeService,
                                MarketRegimeConfig marketRegimeConfig,
                                AccountConfig accountConfig,
                                SellRuleConfig sellRuleConfig,
                                EveningDigestFormatter formatter,
                                NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.positionAlertRepository = positionAlertRepository;
        this.portfolioAlertRepository = portfolioAlertRepository;
        this.indicatorRepository = indicatorRepository;
        this.stockRepository = stockRepository;
        this.positionSummaryProvider = positionSummaryProvider;
        this.marketRegimeService = marketRegimeService;
        this.marketRegimeConfig = marketRegimeConfig;
        this.accountConfig = accountConfig;
        this.sellRuleConfig = sellRuleConfig;
        this.formatter = formatter;
        this.notificationService = notificationService;
    }

    /**
     * @return true if the email was sent, false if notifications are disabled
     * @throws org.springframework.mail.MailException if sending fails; nothing
     *         is marked as notified in that case
     */
    public boolean sendEveningDigest() {
        LocalDate today = LocalDate.now();

        List<Alert> pendingAlerts = alertRepository.findByNotifiedAtIsNull();
        List<PositionAlert> pendingPositionAlerts = positionAlertRepository.findByNotifiedAtIsNull();
        List<PortfolioAlert> pendingPortfolioAlerts = portfolioAlertRepository.findByNotifiedAtIsNull();

        // Alerts som redan hanterats (köpta/avvisade) innan mejlet hann gå visas inte,
        // men markeras ändå som notifierade nedan
        List<BuySignal> buySignals = pendingAlerts.stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .filter(alert -> alert.getEntryPrice() != null && alert.getStopPrice() != null
                        && alert.getShares() != null)
                .map(this::toBuySignal)
                .toList();

        // Varningar för positioner som hunnit stängas är inte längre relevanta
        List<PositionWarning> positionWarnings = pendingPositionAlerts.stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .filter(alert -> alert.getPosition().getStatus() == PositionStatus.OPEN)
                .map(alert -> toPositionWarning(alert, today))
                .toList();

        List<OvernightWarning> overnightWarnings = pendingPortfolioAlerts.stream()
                .filter(alert -> alert.getStatus() == AlertStatus.NEW)
                .map(alert -> new OvernightWarning(alert.getName(), alert.getWeightPercent(),
                        alert.getSuggestedSharesToSell()))
                .toList();

        EveningDigest digest = new EveningDigest(today,
                marketRegimeService.getLatest().map(MarketRegime::getStatus).orElse(null),
                marketRegimeConfig.regimeMode(),
                indexReadings(),
                accountConfig,
                sellRuleConfig,
                buySignals, positionWarnings, overnightWarnings);

        boolean sent = notificationService.send(formatter.format(digest));

        if (sent) {
            markNotified(pendingAlerts, pendingPositionAlerts, pendingPortfolioAlerts);
        }
        return sent;
    }

    private BuySignal toBuySignal(Alert alert) {
        BigDecimal adr = latestIndicator(alert.getStock()).map(Indicator::getAdr20).orElse(null);
        return new BuySignal(alert.getId(), alert.getStock().getSymbol(), setupOf(alert.getType()),
                alert.getEntryPrice(), alert.getStopPrice(), alert.getShares(), alert.getRiskPercent(), adr);
    }

    private PositionWarning toPositionWarning(PositionAlert alert, LocalDate today) {
        Position position = alert.getPosition();
        BigDecimal maValue = latestIndicator(position.getStock())
                .map(indicator -> sellRuleConfig.maPeriod() == 20 ? indicator.getMa20() : indicator.getMa10())
                .orElse(null);
        return new PositionWarning(position.getId(), position.getStock().getSymbol(), position.getSetupType(),
                alert.getType(), alert.getSuggestedStopPrice(), position.getStopPrice(), position.getInitialRisk(),
                ChronoUnit.DAYS.between(position.getOpenedAt().toLocalDate(), today),
                summaryOrNull(position), maValue);
    }

    /** A missing price shouldn't stop the whole email - the warning is shown without the numbers. */
    private PositionSummary summaryOrNull(Position position) {
        try {
            return positionSummaryProvider.buildSummary(position);
        } catch (RuntimeException e) {
            log.warn("Kunde inte räkna sammanfattning för {}: {}", position.getStock().getSymbol(), e.getMessage());
            return null;
        }
    }

    /** The same readings MarketRegimeService judged the regime on, for the explanation. */
    private List<IndexReading> indexReadings() {
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

    private void markNotified(List<Alert> alerts, List<PositionAlert> positionAlerts,
                              List<PortfolioAlert> portfolioAlerts) {
        LocalDateTime now = LocalDateTime.now();
        alerts.forEach(alert -> alert.setNotifiedAt(now));
        positionAlerts.forEach(alert -> alert.setNotifiedAt(now));
        portfolioAlerts.forEach(alert -> alert.setNotifiedAt(now));
        alertRepository.saveAll(alerts);
        positionAlertRepository.saveAll(positionAlerts);
        portfolioAlertRepository.saveAll(portfolioAlerts);
    }

    /** Alert types carry the setup as a prefix, e.g. BREAKOUT_BUY or EPISODIC_PIVOT_BUY. */
    static SetupType setupOf(String alertType) {
        if (alertType.startsWith("PARABOLIC_SHORT")) {
            return SetupType.PARABOLIC_SHORT;
        }
        if (alertType.startsWith("EPISODIC_PIVOT")) {
            return SetupType.EPISODIC_PIVOT;
        }
        return SetupType.BREAKOUT;
    }
}
