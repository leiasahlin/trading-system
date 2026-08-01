package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.MarketRegimeRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.market.MarketRegimeConfig;
import com.qullamaggie.tradingsystem.market.MarketRegimeEvaluator;
import com.qullamaggie.tradingsystem.market.RegimeMode;
import com.qullamaggie.tradingsystem.market.service.MarketRegimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketRegimeServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private IndicatorRepository indicatorRepository;
    @Mock private MarketRegimeRepository marketRegimeRepository;

    private MarketRegimeService service;
    private Stock spy;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    @BeforeEach
    void setUp() {
        MarketRegimeConfig config =
                new MarketRegimeConfig(List.of("SPY"), RegimeMode.ANY, 10, 20);

        service = new MarketRegimeService(stockRepository, indicatorRepository,
                marketRegimeRepository, new MarketRegimeEvaluator(config), config);

        spy = new Stock();
        spy.setSymbol("SPY");
        spy.setType(StockType.INDEX);
    }

    private Indicator indicator(double ma10, double ma20) {
        Indicator indicator = new Indicator();
        indicator.setMa10(BigDecimal.valueOf(ma10));
        indicator.setMa20(BigDecimal.valueOf(ma20));
        return indicator;
    }

    private void stubSaveReturnsArgument() {
        when(marketRegimeRepository.save(any(MarketRegime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void evaluateAndSave_storesRiskOn_whenFastMaAboveSlowMa() {
        stubSaveReturnsArgument();
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(spy))
                .thenReturn(Optional.of(indicator(410, 400)));
        when(marketRegimeRepository.findByDate(TODAY)).thenReturn(Optional.empty());

        MarketRegime result = service.evaluateAndSave(TODAY);

        assertEquals(RegimeStatus.RISK_ON, result.getStatus());
        assertEquals(TODAY, result.getDate());
        assertTrue(result.getDetails().contains("SPY"));
    }

    @Test
    void evaluateAndSave_storesRiskOff_whenFastMaBelowSlowMa() {
        stubSaveReturnsArgument();
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(spy))
                .thenReturn(Optional.of(indicator(390, 400)));
        when(marketRegimeRepository.findByDate(TODAY)).thenReturn(Optional.empty());

        MarketRegime result = service.evaluateAndSave(TODAY);

        assertEquals(RegimeStatus.RISK_OFF, result.getStatus());
    }

    @Test
    void evaluateAndSave_storesRiskOff_whenIndexHasNoIndicatorData() {
        stubSaveReturnsArgument();
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(spy)).thenReturn(Optional.empty());
        when(marketRegimeRepository.findByDate(TODAY)).thenReturn(Optional.empty());

        MarketRegime result = service.evaluateAndSave(TODAY);

        assertEquals(RegimeStatus.RISK_OFF, result.getStatus());
        assertTrue(result.getDetails().contains("ingen indikatordata"));
    }

    @Test
    void evaluateAndSave_storesRiskOff_whenIndexStockRowMissing() {
        stubSaveReturnsArgument();
        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.empty());
        when(marketRegimeRepository.findByDate(TODAY)).thenReturn(Optional.empty());

        MarketRegime result = service.evaluateAndSave(TODAY);

        assertEquals(RegimeStatus.RISK_OFF, result.getStatus());
        verifyNoInteractions(indicatorRepository);
    }

    @Test
    void evaluateAndSave_updatesExistingRow_ratherThanCreatingDuplicate() {
        stubSaveReturnsArgument();
        MarketRegime existing = new MarketRegime();
        existing.setId(1L);
        existing.setDate(TODAY);
        existing.setStatus(RegimeStatus.RISK_OFF);

        when(stockRepository.findBySymbol("SPY")).thenReturn(Optional.of(spy));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(spy))
                .thenReturn(Optional.of(indicator(410, 400)));
        when(marketRegimeRepository.findByDate(TODAY)).thenReturn(Optional.of(existing));

        MarketRegime result = service.evaluateAndSave(TODAY);

        assertEquals(1L, result.getId());   // samma rad, inte en ny
        assertEquals(RegimeStatus.RISK_ON, result.getStatus());
    }

    @Test
    void getLatest_delegatesToRepository() {
        MarketRegime regime = new MarketRegime();
        when(marketRegimeRepository.findTop1ByOrderByDateDesc()).thenReturn(Optional.of(regime));

        assertEquals(Optional.of(regime), service.getLatest());
    }
}
