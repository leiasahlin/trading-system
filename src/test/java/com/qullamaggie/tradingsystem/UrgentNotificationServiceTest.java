package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.notifications.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrgentNotificationServiceTest {

    @Mock private AlertRepository alertRepository;
    @Mock private PositionAlertRepository positionAlertRepository;
    @Mock private PortfolioAlertRepository portfolioAlertRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private StockRepository stockRepository;
    @Mock private PositionSummaryProvider positionSummaryProvider;
    @Mock private NotificationService notificationService;

    private UrgentNotificationService service;
    private Stock mstr;

    @BeforeEach
    void setUp() {
        AccountConfig account = new AccountConfig(
                new BigDecimal("1000000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.20"));
        NotificationDataLoader loader = new NotificationDataLoader(indicatorRepository, stockRepository,
                positionSummaryProvider, new MarketRegimeConfig(List.of("SPY"), RegimeMode.ALL, 10, 20),
                account, new SellRuleConfig(BigDecimal.valueOf(2), 3, 5, 10));
        EmailRenderer renderer = new EmailRenderer();

        service = new UrgentNotificationService(alertRepository, positionAlertRepository, portfolioAlertRepository,
                loader, new UrgentEmailFormatter(new EveningDigestFormatter(renderer), renderer), notificationService);

        mstr = new Stock();
        mstr.setSymbol("MSTR");
    }

    private Position position(PositionStatus status) {
        Position position = new Position();
        position.setId(1L);
        position.setStock(mstr);
        position.setSetupType(SetupType.BREAKOUT);
        position.setStatus(status);
        position.setOpenedAt(LocalDateTime.now().minusDays(2));
        position.setStopPrice(new BigDecimal("163.18"));
        position.setInitialStopPrice(new BigDecimal("163.18"));
        position.setInitialRisk(new BigDecimal("9095"));
        return position;
    }

    private PositionAlert alert(PositionAlertType type, Position position) {
        PositionAlert alert = new PositionAlert();
        alert.setPosition(position);
        alert.setType(type);
        alert.setStatus(AlertStatus.NEW);
        alert.setTriggerPrice(new BigDecimal("162.90"));
        return alert;
    }

    private EmailContent sentEmail() {
        ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
        verify(notificationService).send(captor.capture());
        return captor.getValue();
    }

    // --- Stoppbrott ---

    @Test
    void stopBreach_isMailed_andMarkedNotified() {
        PositionAlert breach = alert(PositionAlertType.STOP_BREACH, position(PositionStatus.OPEN));
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(breach));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertEquals(1, service.sendStopBreaches());

        assertTrue(sentEmail().subject().startsWith("🔴 STOP NÅDD: MSTR på 162,90 $"));
        assertNotNull(breach.getNotifiedAt());
        verify(positionAlertRepository).save(breach);
    }

    @Test
    void otherWarningTypes_waitForTheEveningEmail() {
        PositionAlert trim = alert(PositionAlertType.TRIM, position(PositionStatus.OPEN));
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(trim));

        assertEquals(0, service.sendStopBreaches());

        verifyNoInteractions(notificationService);
        assertNull(trim.getNotifiedAt());
    }

    @Test
    void stopBreach_positionAlreadyClosed_isNotMailed() {
        PositionAlert breach = alert(PositionAlertType.STOP_BREACH, position(PositionStatus.STOPPED));
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(breach));

        assertEquals(0, service.sendStopBreaches());

        verifyNoInteractions(notificationService);
    }

    @Test
    void oneMailPerBreach() {
        PositionAlert first = alert(PositionAlertType.STOP_BREACH, position(PositionStatus.OPEN));
        PositionAlert second = alert(PositionAlertType.STOP_BREACH, position(PositionStatus.OPEN));
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(first, second));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertEquals(2, service.sendStopBreaches());

        verify(notificationService, times(2)).send(any(EmailContent.class));
    }

    @Test
    void sendFails_notMarked_soEveningEmailPicksItUp() {
        PositionAlert breach = alert(PositionAlertType.STOP_BREACH, position(PositionStatus.OPEN));
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(breach));
        when(notificationService.send(any(EmailContent.class))).thenThrow(new MailSendException("nere"));

        assertThrows(MailSendException.class, () -> service.sendStopBreaches());

        assertNull(breach.getNotifiedAt());
    }

    @Test
    void notificationsDisabled_nothingMarked() {
        PositionAlert breach = alert(PositionAlertType.STOP_BREACH, position(PositionStatus.OPEN));
        when(positionAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(breach));
        when(notificationService.send(any(EmailContent.class))).thenReturn(false);

        assertEquals(0, service.sendStopBreaches());

        assertNull(breach.getNotifiedAt());
        verify(positionAlertRepository, never()).save(any());
    }

    // --- Övernatt ---

    @Test
    void overnightWarning_isMailed_andMarkedNotified() {
        PortfolioAlert warning = new PortfolioAlert();
        warning.setName("MicroStrategy");
        warning.setWeightPercent(new BigDecimal("34"));
        warning.setSuggestedSharesToSell(210);
        when(portfolioAlertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(warning));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertEquals(1, service.sendOvernightWarnings());

        assertEquals("🔴 ÖVERNATT: minska MicroStrategy med 210 st före stängning", sentEmail().subject());
        assertNotNull(warning.getNotifiedAt());
    }

    // --- EP ---

    private Alert buyAlert(String type) {
        Alert alert = new Alert();
        alert.setId(7L);
        alert.setStock(mstr);
        alert.setType(type);
        alert.setEntryPrice(new BigDecimal("48.20"));
        alert.setStopPrice(new BigDecimal("45.90"));
        alert.setShares(100);
        alert.setRiskPercent(new BigDecimal("0.01"));
        alert.setStatus(AlertStatus.NEW);
        return alert;
    }

    @Test
    void epSignal_isMailed_breakoutWaitsForEvening() {
        Alert ep = buyAlert("EPISODIC_PIVOT_BUY");
        Alert breakout = buyAlert("BREAKOUT_BUY");
        when(alertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(ep, breakout));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertEquals(1, service.sendEpSignals());

        assertTrue(sentEmail().subject().startsWith("🟡 EP-SIGNAL: köp MSTR över 48,20 $"));
        assertNotNull(ep.getNotifiedAt());
        assertNull(breakout.getNotifiedAt());
    }

    @Test
    void sendAllPending_sumsAllKinds() {
        when(positionAlertRepository.findByNotifiedAtIsNull())
                .thenReturn(List.of(alert(PositionAlertType.STOP_BREACH, position(PositionStatus.OPEN))));
        when(alertRepository.findByNotifiedAtIsNull()).thenReturn(List.of(buyAlert("EPISODIC_PIVOT_BUY")));
        when(notificationService.send(any(EmailContent.class))).thenReturn(true);

        assertEquals(2, service.sendAllPending());
    }
}
