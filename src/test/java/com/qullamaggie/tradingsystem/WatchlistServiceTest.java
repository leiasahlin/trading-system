package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Indicator;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.repository.IndicatorRepository;
import com.qullamaggie.tradingsystem.data.repository.StockRepository;
import com.qullamaggie.tradingsystem.scanner.BreakoutCriterion;
import com.qullamaggie.tradingsystem.scanner.BreakoutEvaluator;
import com.qullamaggie.tradingsystem.scanner.BreakoutScanConfig;
import com.qullamaggie.tradingsystem.scanner.WatchlistCandidate;
import com.qullamaggie.tradingsystem.scanner.WatchlistConfig;
import com.qullamaggie.tradingsystem.scanner.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The repositories are mocked, but the BreakoutEvaluator is real: the point is
 * to check that the watchlist and the scanner judge stocks by the same rules.
 *
 * Every stock closes at 100. The breakout level (consolidation high) is set
 * per stock, so the distance is easy to read: level 103 -> 3 % below.
 */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private DailyPriceRepository dailyPriceRepository;
    @Mock private IndicatorRepository indicatorRepository;

    private WatchlistService service;

    @BeforeEach
    void setUp() {
        service = serviceWithMax(10);
    }

    // ---------- Hjälpmetoder ----------

    /** Min 5 of 7 criteria, as in the real settings; the cap varies per test. */
    private WatchlistService serviceWithMax(int maxCandidates) {
        BreakoutEvaluator evaluator = new BreakoutEvaluator(new BreakoutScanConfig(
                new BigDecimal("30"),   // minPriorMove
                new BigDecimal("100"),  // maxPriorMove
                new BigDecimal("3"),    // minAdr
                1_000_000L,             // minAvgVolume
                new BigDecimal("15"),   // maxConsolidationRange
                new BigDecimal("25"),   // maxPullback
                new BigDecimal("0.8"))); // maxVolumeContraction

        return new WatchlistService(stockRepository, dailyPriceRepository, indicatorRepository,
                evaluator, new WatchlistConfig(5, maxCandidates));
    }

    private Stock stock(String symbol) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        return stock;
    }

    /** An indicator where all seven criteria pass, with the given breakout level. */
    private Indicator passingIndicator(String breakoutLevel) {
        Indicator i = new Indicator();
        i.setPriorMove(new BigDecimal("45"));
        i.setVolumeAvg20(2_000_000L);
        i.setAdr20(new BigDecimal("5"));
        i.setMa10(new BigDecimal("95"));
        i.setMa20(new BigDecimal("90"));
        i.setConsolidationRange(new BigDecimal("8"));
        i.setPullback(new BigDecimal("5"));
        i.setVolumeContraction(new BigDecimal("0.6"));
        i.setConsolidationHigh(breakoutLevel == null ? null : new BigDecimal(breakoutLevel));
        return i;
    }

    /** Makes an otherwise passing indicator fail the given number of criteria (0-4). */
    private Indicator failing(Indicator i, int criteriaToFail) {
        if (criteriaToFail >= 1) i.setAdr20(new BigDecimal("2"));                  // ADR
        if (criteriaToFail >= 2) i.setVolumeContraction(new BigDecimal("0.9"));    // VOLUME_CONTRACTION
        if (criteriaToFail >= 3) i.setPullback(new BigDecimal("30"));              // PULLBACK
        if (criteriaToFail >= 4) i.setConsolidationRange(new BigDecimal("20"));    // CONSOLIDATION_RANGE
        return i;
    }

    /** Stubs the repositories so the stock has the given indicator and a close of 100. */
    private void given(Stock stock, Indicator indicator) {
        DailyPrice price = new DailyPrice();
        price.setClose(new BigDecimal("100"));
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(indicator));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(stock)).thenReturn(Optional.of(price));
    }

    private void eligible(Stock... stocks) {
        when(stockRepository.findByEligibleTrue()).thenReturn(List.of(stocks));
    }

    private List<String> symbols(List<WatchlistCandidate> candidates) {
        return candidates.stream().map(c -> c.stock().getSymbol()).toList();
    }

    // ---------- Vilka kommer med ----------

    @Test
    void fiveOfSeven_isIncluded_withMissingCriteriaAndDistance() {
        Stock nvda = stock("NVDA");
        eligible(nvda);
        given(nvda, failing(passingIndicator("103"), 2));

        List<WatchlistCandidate> result = service.findTopCandidates(Set.of());

        assertEquals(1, result.size());
        WatchlistCandidate candidate = result.get(0);
        assertEquals(5, candidate.evaluation().passedCount());
        assertEquals(Set.of(BreakoutCriterion.ADR, BreakoutCriterion.VOLUME_CONTRACTION),
                candidate.evaluation().failed());
        assertEquals(0, new BigDecimal("3").compareTo(candidate.distanceToBreakoutPercent()));
    }

    @Test
    void fourOfSeven_isLeftOut() {
        Stock nvda = stock("NVDA");
        eligible(nvda);
        given(nvda, failing(passingIndicator("103"), 3));

        assertTrue(service.findTopCandidates(Set.of()).isEmpty());
    }

    @Test
    void sevenOfSeven_isIncluded() {
        // I björnmarknad blir en sådan aktie ingen köpsignal, så listan är enda stället den syns
        Stock nvda = stock("NVDA");
        eligible(nvda);
        given(nvda, passingIndicator("103"));

        List<WatchlistCandidate> result = service.findTopCandidates(Set.of());

        assertEquals(1, result.size());
        assertTrue(result.get(0).evaluation().allPassed());
    }

    @Test
    void closedAboveBreakoutLevel_distanceIsNegative() {
        Stock nvda = stock("NVDA");
        eligible(nvda);
        given(nvda, passingIndicator("98"));

        BigDecimal distance = service.findTopCandidates(Set.of()).get(0).distanceToBreakoutPercent();

        assertEquals(0, new BigDecimal("-2").compareTo(distance));
    }

    // ---------- Vilka hoppas över ----------

    @Test
    void excludedSymbol_isSkippedWithoutLookingItUp() {
        Stock nvda = stock("NVDA");
        Stock amd = stock("AMD");
        eligible(nvda, amd);
        given(amd, passingIndicator("103"));

        List<WatchlistCandidate> result = service.findTopCandidates(Set.of("NVDA"));

        assertEquals(List.of("AMD"), symbols(result));
        verify(indicatorRepository, never()).findTop1ByStockOrderByDateDesc(nvda);
    }

    @Test
    void noFlag_isLeftOut() {
        Indicator noFlag = passingIndicator("103");
        noFlag.setConsolidationRange(null);
        Stock nvda = stock("NVDA");
        eligible(nvda);
        given(nvda, noFlag);

        assertTrue(service.findTopCandidates(Set.of()).isEmpty());
    }

    @Test
    void missingBreakoutLevel_isLeftOut() {
        Stock nvda = stock("NVDA");
        eligible(nvda);
        given(nvda, passingIndicator(null));

        assertTrue(service.findTopCandidates(Set.of()).isEmpty());
    }

    @Test
    void missingPrice_isLeftOut() {
        Stock nvda = stock("NVDA");
        eligible(nvda);
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(nvda))
                .thenReturn(Optional.of(passingIndicator("103")));
        when(dailyPriceRepository.findTop1ByStockOrderByDateDesc(nvda)).thenReturn(Optional.empty());

        assertTrue(service.findTopCandidates(Set.of()).isEmpty());
    }

    @Test
    void oneStockFails_theOthersAreStillIncluded() {
        Stock broken = stock("BROKEN");
        Stock amd = stock("AMD");
        eligible(broken, amd);
        when(indicatorRepository.findTop1ByStockOrderByDateDesc(broken))
                .thenThrow(new RuntimeException("databasfel"));
        given(amd, passingIndicator("103"));

        assertEquals(List.of("AMD"), symbols(service.findTopCandidates(Set.of())));
    }

    // ---------- Sortering och tak ----------

    @Test
    void sortedByCriteriaFirst_thenByDistanceRegardlessOfDirection() {
        Stock six = stock("SIX");          // 6 av 7, 1 % under
        Stock sevenFar = stock("FAR");     // 7 av 7, 8 % under
        Stock sevenNear = stock("NEAR");   // 7 av 7, 2 % över (-2)
        Stock sevenMid = stock("MID");     // 7 av 7, 3 % under
        eligible(six, sevenFar, sevenNear, sevenMid);
        given(six, failing(passingIndicator("101"), 1));
        given(sevenFar, passingIndicator("108"));
        given(sevenNear, passingIndicator("98"));
        given(sevenMid, passingIndicator("103"));

        List<WatchlistCandidate> result = service.findTopCandidates(Set.of());

        // 7 av 7 före 6 av 7, även om SIX ligger närmast. Bland 7 av 7: 2 % över före 3 % under
        assertEquals(List.of("NEAR", "MID", "FAR", "SIX"), symbols(result));
    }

    @Test
    void cappedAtMaxCandidates_keepsTheBest() {
        service = serviceWithMax(2);

        Stock a = stock("A");   // 5 av 7
        Stock b = stock("B");   // 7 av 7
        Stock c = stock("C");   // 6 av 7
        eligible(a, b, c);
        given(a, failing(passingIndicator("103"), 2));
        given(b, passingIndicator("103"));
        given(c, failing(passingIndicator("103"), 1));

        assertEquals(List.of("B", "C"), symbols(service.findTopCandidates(Set.of())));
    }

    @Test
    void noEligibleStocks_emptyList() {
        eligible();

        assertTrue(service.findTopCandidates(Set.of()).isEmpty());
    }
}