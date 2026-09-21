package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.dto.PortfolioHolding;
import com.qullamaggie.tradingsystem.data.dto.PortfolioSnapshot;
import com.qullamaggie.tradingsystem.data.entity.PortfolioAlert;
import com.qullamaggie.tradingsystem.data.provider.PortfolioDataProvider;
import com.qullamaggie.tradingsystem.data.repository.PortfolioAlertRepository;
import com.qullamaggie.tradingsystem.portfolio.service.OvernightExposureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OvernightExposureServiceTest {

    @Mock private PortfolioDataProvider portfolioDataProvider;
    @Mock
    private PortfolioAlertRepository portfolioAlertRepository;

    private OvernightExposureService service;

    @BeforeEach
    void setUp() {
        AccountConfig config = new AccountConfig(
                new BigDecimal("100000"), new BigDecimal("0.20"),
                new BigDecimal("0.005"), new BigDecimal("0.01"),
                new BigDecimal("0.30"), new BigDecimal("0.20"));
        service = new OvernightExposureService(portfolioDataProvider, portfolioAlertRepository, config);
    }

    private PortfolioHolding holding(String isin, String marketValue, String price) {
        return new PortfolioHolding(isin, "Testbolag", 0, null,
                new BigDecimal(price), new BigDecimal(marketValue));
    }

    private void snapshot(String accountValue, PortfolioHolding... holdings) {
        when(portfolioDataProvider.fetchSnapshot())
                .thenReturn(new PortfolioSnapshot(List.of(holdings), new BigDecimal(accountValue)));
    }

    @Test
    void alertsWithTrimToTarget_whenPositionExceedsCeiling() {
        // 400 000 av 1 000 000 = 40 %. Mål 20 % = 200 000 -> sälj 200 000 / 100 = 2 000 aktier
        snapshot("1000000", holding("SE0000000001", "400000", "100"));
        when(portfolioAlertRepository.existsByIsinAndAlertDate(any(), any())).thenReturn(false);

        service.checkOvernightExposure();

        ArgumentCaptor<PortfolioAlert> captor = ArgumentCaptor.forClass(PortfolioAlert.class);
        verify(portfolioAlertRepository).save(captor.capture());
        assertEquals(2000, captor.getValue().getSuggestedSharesToSell());
        assertEquals(0, new BigDecimal("40").compareTo(captor.getValue().getWeightPercent()));
    }

    @Test
    void roundsSharesUp_soPositionEndsAtOrBelowTarget() {
        // 400 050 - 200 000 = 200 050 / 100 = 2 000,5 -> 2 001 aktier
        snapshot("1000000", holding("SE0000000001", "400050", "100"));
        when(portfolioAlertRepository.existsByIsinAndAlertDate(any(), any())).thenReturn(false);

        service.checkOvernightExposure();

        ArgumentCaptor<PortfolioAlert> captor = ArgumentCaptor.forClass(PortfolioAlert.class);
        verify(portfolioAlertRepository).save(captor.capture());
        assertEquals(2001, captor.getValue().getSuggestedSharesToSell());
    }

    @Test
    void noAlert_atExactlyTheCeiling() {
        snapshot("1000000", holding("SE0000000001", "300000", "100"));

        service.checkOvernightExposure();

        verify(portfolioAlertRepository, never()).save(any());
    }

    @Test
    void noDuplicate_whenAlreadyAlertedToday() {
        snapshot("1000000", holding("SE0000000001", "400000", "100"));
        when(portfolioAlertRepository.existsByIsinAndAlertDate(eq("SE0000000001"), any())).thenReturn(true);

        service.checkOvernightExposure();

        verify(portfolioAlertRepository, never()).save(any());
    }

    @Test
    void skipsCheck_whenAccountValueIsZero() {
        snapshot("0", holding("SE0000000001", "400000", "100"));

        service.checkOvernightExposure();

        verifyNoInteractions(portfolioAlertRepository);
    }
}