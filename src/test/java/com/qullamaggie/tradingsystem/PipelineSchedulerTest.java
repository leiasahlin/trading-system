package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.notifications.EveningDigestService;
import com.qullamaggie.tradingsystem.notifications.FailureNotifier;
import com.qullamaggie.tradingsystem.notifications.ScheduledJob;
import com.qullamaggie.tradingsystem.pipeline.PipelineScheduler;
import com.qullamaggie.tradingsystem.pipeline.PipelineService;
import com.qullamaggie.tradingsystem.portfolio.service.IntradayMonitoringService;
import com.qullamaggie.tradingsystem.portfolio.service.OvernightExposureService;
import com.qullamaggie.tradingsystem.scanner.service.ParabolicIntradayService;
import com.qullamaggie.tradingsystem.universe.service.MarketDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

/**
 * The intraday loop isn't tested here: it checks the real New York clock,
 * so the outcome would depend on when the test runs.
 */
@ExtendWith(MockitoExtension.class)
class PipelineSchedulerTest {

    @Mock private PipelineService pipelineService;
    @Mock private IntradayMonitoringService intradayMonitoringService;
    @Mock private ParabolicIntradayService parabolicIntradayService;
    @Mock private MarketDiscoveryService marketDiscoveryService;
    @Mock private OvernightExposureService overnightExposureService;
    @Mock private EveningDigestService eveningDigestService;
    @Mock private FailureNotifier failureNotifier;

    private PipelineScheduler scheduler;

    @BeforeEach
    void setUp() {
        // ParabolicShortConfig används bara av intradagsloopen, som inte testas här
        scheduler = new PipelineScheduler(pipelineService, intradayMonitoringService, parabolicIntradayService,
                marketDiscoveryService, overnightExposureService, eveningDigestService, failureNotifier, null);
    }

    @Test
    void eveningPipelineSucceeds_sendsDigest_noFailureMail() {
        scheduler.runEveningPipeline();

        verify(pipelineService).runForAllStocks();
        verify(eveningDigestService).sendEveningDigest();
        verifyNoInteractions(failureNotifier);
    }

    @Test
    void eveningPipelineFails_reportsFailure_andSkipsDigest() {
        IllegalStateException error = new IllegalStateException("Ingen prisdata tillgänglig för SPY");
        doThrow(error).when(pipelineService).runForAllStocks();

        assertDoesNotThrow(() -> scheduler.runEveningPipeline());

        verify(failureNotifier).notifyFailure(same(ScheduledJob.EVENING_PIPELINE), same(error));
        verify(eveningDigestService, never()).sendEveningDigest();
    }

    @Test
    void digestFails_reportsDigestFailure() {
        when(eveningDigestService.sendEveningDigest()).thenThrow(new MailSendException("nere"));

        scheduler.runEveningPipeline();

        verify(failureNotifier).notifyFailure(same(ScheduledJob.EVENING_DIGEST), any(MailSendException.class));
    }

    @Test
    void episodicPivotFails_isReported() {
        doThrow(new RuntimeException("API svarar inte")).when(pipelineService).runEpisodicPivotScanOnly();

        scheduler.runMorningEpisodicPivot();

        verify(failureNotifier).notifyFailure(same(ScheduledJob.EP_SCAN), any(RuntimeException.class));
    }

    @Test
    void overnightCheckFails_isReported() {
        doThrow(new RuntimeException("Avanza svarar inte")).when(overnightExposureService).checkOvernightExposure();

        scheduler.runOvernightExposureCheck();

        verify(failureNotifier).notifyFailure(same(ScheduledJob.OVERNIGHT_CHECK), any(RuntimeException.class));
    }

    @Test
    void discoveryFails_isReported() {
        doThrow(new RuntimeException("Twelve Data svarar inte")).when(marketDiscoveryService).discoverAndSaveCandidates();

        scheduler.runMarketDiscovery();

        verify(failureNotifier).notifyFailure(same(ScheduledJob.DISCOVERY), any(RuntimeException.class));
    }
}
