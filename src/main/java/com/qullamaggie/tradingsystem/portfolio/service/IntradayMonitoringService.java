package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.dto.IntradayBar;
import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import com.qullamaggie.tradingsystem.data.repository.PositionAlertRepository;
import com.qullamaggie.tradingsystem.data.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Intraday guard for open positions: alerts when a completed 5-minute bar
 * closes at or below the position's stop. Sell rules (trim, trailing, exit)
 * remain end-of-day decisions per the methodology - this loop only watches
 * for stop breaches so they're seen immediately rather than at the evening run.
 */
@Service
public class IntradayMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(IntradayMonitoringService.class);

    private final PositionRepository positionRepository;
    private final PositionAlertRepository positionAlertRepository;
    private final MarketDataProvider marketDataProvider;

    public IntradayMonitoringService(PositionRepository positionRepository,
                                     PositionAlertRepository positionAlertRepository,
                                     MarketDataProvider marketDataProvider) {
        this.positionRepository = positionRepository;
        this.positionAlertRepository = positionAlertRepository;
        this.marketDataProvider = marketDataProvider;
    }

    public void checkAllOpenPositions() {
        for (Position position : positionRepository.findByStatus(PositionStatus.OPEN)) {
            checkPosition(position);
        }
    }

    private void checkPosition(Position position) {
        BigDecimal stop = position.getStopPrice();
        if (stop == null) {
            log.warn("Position {} saknar stopPrice - hoppar över intradagsbevakning",
                    position.getStock().getSymbol());
            return;
        }
        if (positionAlertRepository.existsByPositionAndTypeAndStatus(
                position, PositionAlertType.STOP_BREACH, AlertStatus.NEW)) {
            return;
        }

        List<IntradayBar> bars = marketDataProvider.fetchIntradayBars(position.getStock().getSymbol());
        if (bars.isEmpty()) {
            return;
        }

        BigDecimal lastClose = bars.getLast().close();
        // Endast långa positioner: stop ligger UNDER entry. Parabolic shorts
        // bevakas inte (känd begränsning sedan tidigare).
        if (lastClose.compareTo(stop) <= 0) {
            PositionAlert alert = new PositionAlert();
            alert.setPosition(position);
            alert.setType(PositionAlertType.STOP_BREACH);
            alert.setStatus(AlertStatus.NEW);
            alert.setCreatedAt(LocalDateTime.now());
            positionAlertRepository.save(alert);
        }
    }
}
