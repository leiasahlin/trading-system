package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import com.qullamaggie.tradingsystem.notifications.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import com.qullamaggie.tradingsystem.portfolio.SellRuleConfig;
import com.qullamaggie.tradingsystem.portfolio.service.PositionSummaryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Uses the real formatter and renderer, so the tests check what actually
 * ends up in the email rather than what was handed to a mock. Repositories
 * not stubbed return empty lists and Optional.empty() by default.
 */
@ExtendWith(MockitoExtension.class)
class EveningDigestServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private PositionAlertRepository positionAlertRepository;
    @Mock private PortfolioAlertRepository portfolioAlertRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private StockRepository stockRepository;
    @Mock private PositionSummaryProvider positionSummaryProvider;
    @Mock private MarketRegimeService marketRegimeService;
    @Mock private NotificationService notificationService;

    private EveningDigestService service;
    private Stock mstr;

    @BeforeEach
    void setUp() {
        AccountConfig accountConfig = new AccountConfig(
                new BigDecimal("1000000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.20"));

        NotificationDataLoader loader = new NotificationDataLoader(indicatorRepository, stockRepository,
                positionSummaryProvider, new MarketRegimeConfig(List.of("SPY"), RegimeMode.ALL, 10, 20),
                accountConfig, new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10));

        service = new EveningDigestService(alertRepository, positionAlertRepository, portfolioAlertRepository,
                marketRegimeService, loader, new EveningDigestFormatter(new EmailRenderer()), notificationService);

        mstr = new Stock();
        mstr.setSymbol("MSTR");
    }

    private Alert buyAlert(AlertStatus status) {
        Alert alert = new Alert();
        alert.setId(12L);
        alert.setStock(mstr);
        alert.setType("BREAKOUT_BUY");
        alert.setEntryPrice(new BigDecimal("171.19"));
        alert.setStopPrice(new BigDecimal("163.18"));
        alert.setShares(1168);
        alert.setRiskPercent(new BigDecimal("0.0100"));
        alert.setStatus(status);
        return alert;
    }

    private Position openPosition() {
        Position position = new Position();
        position.setId(1L);
        position.setStock(mstr);
        position.setSetupType(SetupType.BREAKOUT);
        position.setStatus(PositionStatus.OPEN);
        position.setOpenedAt(LocalDateTime.now().minusDays(4));
        position.setStopPrice(new BigDecimal("95"));
        position.setInitialStopPrice(new BigDecimal("95"));
        position.setInitialRisk(new BigDecimal("2000"));
        return position;
    }

    private PositionAlert trimAlert(Position position) {
        PositionAlert alert = new PositionAlert();
        alert.setPosition(position);
        alert.setType(PositionAlertType.TRIM);
        alert.setStatus(AlertStatus.NEW);
        return alert;
    }

    private EmailContent sentEmail() {
        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(notificationService).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void newAlert_isMailedWithAdr_andMarkedNotified() {
        Alert alert = buyAlert(AlertStatus.NEW);
        Indicator indicator = new Indicator();
        indicator.setAdr20(new BigDecimal("6.1"));
        when(alertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(alert));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(mstr)).thenReturn(Optional.of(indicator));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertTrue(service.sendEveningDigest());

        EmailContent email = sentEmail();
        assertTrue(email.subject().startsWith("🟢 Kväll "));
        assertTrue(email.subject().endsWith(": köp MSTR"));
        assertTrue(email.text().contains("Köp: 1 168 st när kursen går över 171,19 $"));
        assertTrue(email.text().contains("ADR (6,1 %). ✔"));
        assertNotNull(alert.getNotifiedAt());
        verify(alertRepository).saveAll(List.of(alert));
    }

    @Test
    void notificationsDisabled_nothingMarked() {
        Alert alert = buyAlert(AlertStatus.NEW);
        when(alertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(alert));
        when(notificationService.send(any(EmailContent.class))).thenReturn(false);

        assertFalse(service.sendEveningDigest());

        assertNull(alert.getNotifiedAt());
        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    void sendingFails_nothingMarked_soItIsRetriedNextTime() {
        Alert alert = buyAlert(AlertStatus.NEW);
        when(alertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(alert));
        when(notificationService.send(any(EmailContent.class))).thenThrow(new MailSendException("nere"));

        assertThrows(MailSendException.class, () -> service.sendEveningDigest());

        assertNull(alert.getNotifiedAt());
        verify(alertRepository, never()).saveAll(any());
    }

    @Test
    void alreadyExecutedAlert_isNotShown_butStillMarked() {
        Alert executed = buyAlert(AlertStatus.EXECUTED);
        when(alertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(executed));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        service.sendEveningDigest();

        assertTrue(sentEmail().subject().endsWith("ingen åtgärd"));
        assertNotNull(executed.getNotifiedAt());
    }

    @Test
    void warningForClosedPosition_isNotShown_butStillMarked() {
        Position closed = openPosition();
        closed.setStatus(PositionStatus.CLOSED);
        PositionAlert trim = trimAlert(closed);
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(trim));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        service.sendEveningDigest();

        assertTrue(sentEmail().subject().endsWith("ingen åtgärd"));
        assertNotNull(trim.getNotifiedAt());
    }

    @Test
    void trimWarning_usesSummaryAndPosition() {
        Position position = openPosition();
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(trimAlert(position)));
        when(positionSummaryProvider.buildSummary(position)).thenReturn(new PositionSummary(
                new BigDecimal("100"), new BigDecimal("123"), 400,
                BigDecimal.ZERO, new BigDecimal("9200"), new BigDecimal("4.6")));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        service.sendEveningDigest();

        String text = sentEmail().text();
        assertTrue(text.contains("SÄLJ AV MSTR (trim, ägd 4 dagar)"));
        assertTrue(text.contains("Sälj: 134–200 av dina 400 st"));
        assertTrue(text.contains("Du är upp 9 200 $. Vid köpet riskerade du 2 000 $"));
        assertTrue(text.contains("/api/positions/1/transactions"));
    }

    @Test
    void positionWithoutPriceData_warningStillShown() {
        Position position = openPosition();
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(trimAlert(position)));
        when(positionSummaryProvider.buildSummary(position))
                .thenThrow(new IllegalStateException("Ingen prisdata tillgänglig för MSTR"));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertTrue(service.sendEveningDigest());

        assertTrue(sentEmail().text().contains("Sälj: 1/3–1/2 av positionen"));
    }

    @Test
    void marketExplanation_usesIndexIndicators() {
        Stock spy = new Stock();
        spy.setSymbol("SPY");
        Indicator indicator = new Indicator();
        indicator.setMa10(new BigDecimal("768.4780"));
        indicator.setMa20(new BigDecimal("765.6395"));
        MarketRegime regime = new MarketRegime();
        regime.setStatus(RegimeStatus.RISK_ON);
        when(marketRegimeService.getLatest()).thenReturn(Optional.of(regime));
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(spy)).thenReturn(Optional.of(indicator));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        service.sendEveningDigest();

        String text = sentEmail().text();
        assertTrue(text.contains("Marknaden går uppåt."));
        assertTrue(text.contains("SPY: 10-dagarssnittet 768,48 ligger över 20-dagarssnittet 765,64 → uppåt."));
    }

    // --- Påminnelse om stoppbrott ---

    private PositionAlert stopBreach(Position position, LocalDateTime notifiedAt) {
        PositionAlert alert = new PositionAlert();
        alert.setPosition(position);
        alert.setType(PositionAlertType.STOP_BREACH);
        alert.setStatus(AlertStatus.NEW);
        alert.setTriggerPrice(new BigDecimal("94"));
        alert.setCreatedAt(LocalDateTime.now().minusDays(1));
        alert.setNotifiedAt(notifiedAt);
        return alert;
    }

    @Test
    void mailedStopBreach_positionStillOpen_isRepeatedWithRedSubject() {
        Position position = openPosition();
        when(positionAlertRepository.findByTypeAndStatus(PositionAlertType.STOP_BREACH, AlertStatus.NEW))
                .thenReturn(List.of(stopBreach(position, LocalDateTime.now().minusDays(1))));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        service.sendEveningDigest();

        EmailContent email = sentEmail();
        assertTrue(email.subject().startsWith("🔴 Kväll "));
        assertTrue(email.subject().contains("stop nådd MSTR (ej sålt)"));
        assertTrue(email.text().contains("Kurs vid larmet: 94,00 $"));
    }

    @Test
    void stopBreach_positionClosed_isNotRepeated() {
        Position closed = openPosition();
        closed.setStatus(PositionStatus.STOPPED);
        when(positionAlertRepository.findByTypeAndStatus(PositionAlertType.STOP_BREACH, AlertStatus.NEW))
                .thenReturn(List.of(stopBreach(closed, LocalDateTime.now().minusDays(1))));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        service.sendEveningDigest();

        assertTrue(sentEmail().subject().endsWith("ingen åtgärd"));
    }
}
