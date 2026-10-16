package com.qullamaggie.tradingsystem.pipeline;

import com.qullamaggie.tradingsystem.notifications.EveningDigestService;
import com.qullamaggie.tradingsystem.notifications.FailureNotifier;
import com.qullamaggie.tradingsystem.notifications.ScheduledJob;
import com.qullamaggie.tradingsystem.portfolio.service.IntradayMonitoringService;
import com.qullamaggie.tradingsystem.portfolio.service.OvernightExposureService;
import com.qullamaggie.tradingsystem.scanner.ParabolicShortConfig;
import com.qullamaggie.tradingsystem.scanner.service.ParabolicIntradayService;
import com.qullamaggie.tradingsystem.universe.service.MarketDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Runs the scheduled jobs. A job that fails is logged and reported by email
 * through FailureNotifier - PipelineService itself stays unprotected on
 * purpose, so a broken step stops the run instead of being skipped quietly.
 */
@Component
public class PipelineScheduler {
    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);

    private final PipelineService pipelineService;
    private final IntradayMonitoringService intradayMonitoringService;
    private final ParabolicIntradayService parabolicIntradayService;
    private final MarketDiscoveryService marketDiscoveryService;
    private final OvernightExposureService overnightExposureService;
    private final EveningDigestService eveningDigestService;
    private final FailureNotifier failureNotifier;
    private final ParabolicShortConfig parabolicConfig;

    public PipelineScheduler(PipelineService pipelineService, IntradayMonitoringService intradayMonitoringService,
                             ParabolicIntradayService parabolicIntradayService, MarketDiscoveryService marketDiscoveryService,
                             OvernightExposureService overnightExposureService, EveningDigestService eveningDigestService,
                             FailureNotifier failureNotifier, ParabolicShortConfig parabolicConfig) {
        this.pipelineService = pipelineService;
        this.intradayMonitoringService = intradayMonitoringService;
        this.parabolicIntradayService = parabolicIntradayService;
        this.marketDiscoveryService = marketDiscoveryService;
        this.overnightExposureService = overnightExposureService;
        this.eveningDigestService = eveningDigestService;
        this.failureNotifier = failureNotifier;
        this.parabolicConfig = parabolicConfig;
    }

    /**
     * Runs the full breakout + universe + position-monitoring pipeline once
     * daily after the US market close, with a buffer for Twelve Data to finalize
     * the day's bar, then sends the evening email.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/New_York")
    public void runEveningPipeline() {
        boolean pipelineSucceeded = run(ScheduledJob.EVENING_PIPELINE, pipelineService::runForAllStocks);
        // Utan en lyckad körning vore ett "ingen åtgärd"-mejl missvisande - felmejlet räcker
        if (pipelineSucceeded) {
            run(ScheduledJob.EVENING_DIGEST, eveningDigestService::sendEveningDigest);
        }
    }

    /**
     * Runs the episodic pivot scan shortly after the US market open, once the
     * configured opening-range window has elapsed.
     */
    @Scheduled(cron = "0 45 9 * * MON-FRI", zone = "America/New_York")
    public void runMorningEpisodicPivot() {
        run(ScheduledJob.EP_SCAN, pipelineService::runEpisodicPivotScanOnly);
    }

    /**
     * Weekly market-wide discovery. The symbol universe changes slowly and a stock
     * that fails the screen today rarely passes tomorrow, so weekly keeps API usage
     * down without missing much. Runs Sunday evening so the week starts with a
     * current candidate list.
     */
    @Scheduled(cron = "0 0 18 * * SUN", zone = "America/New_York")
    public void runMarketDiscovery() {
        run(ScheduledJob.DISCOVERY, marketDiscoveryService::discoverAndSaveCandidates);
    }

    /**
     * Overnight exposure check 30 minutes before the close, leaving time to trim
     * before gap risk applies.
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "America/New_York")
    public void runOvernightExposureCheck() {
        run(ScheduledJob.OVERNIGHT_CHECK, overnightExposureService::checkOvernightExposure);
    }

    @Scheduled(cron = "0 */5 9-16 * * MON-FRI", zone = "America/New_York")
    public void runIntradayLoop() {
        LocalTime now = LocalTime.now(ZoneId.of("America/New_York"));
        if (now.isBefore(LocalTime.of(9, 35)) || now.isAfter(LocalTime.of(16, 0))) {
            return;
        }
        run(ScheduledJob.INTRADAY, () -> {
            intradayMonitoringService.checkAllOpenPositions();
            // Avstängt tills positionsbevakningen klarar blankning
            if (parabolicConfig.enabled()) {
                parabolicIntradayService.checkCandidates();
            }
        });
    }

    /** @return true if the job finished, false if it failed (and was reported) */
    private boolean run(ScheduledJob job, Runnable task) {
        try {
            task.run();
            return true;
        } catch (RuntimeException e) {
            log.error("{} misslyckades", job.displayName(), e);
            failureNotifier.notifyFailure(job, e);
            return false;
        }
    }
}
