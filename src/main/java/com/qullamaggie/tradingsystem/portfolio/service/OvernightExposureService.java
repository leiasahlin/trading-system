package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.alerts.AccountConfig;
import com.qullamaggie.tradingsystem.data.dto.PortfolioHolding;
import com.qullamaggie.tradingsystem.data.dto.PortfolioSnapshot;
import com.qullamaggie.tradingsystem.data.entity.PortfolioAlert;
import com.qullamaggie.tradingsystem.data.provider.PortfolioDataProvider;
import com.qullamaggie.tradingsystem.data.repository.PortfolioAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Warns before the close when a single holding exceeds the overnight exposure
 * limit, with a suggested trim back to the target weight. Selling converts
 * shares to cash, so account value is unchanged and the target is simply
 * target weight x account value.
 */
@Service
public class OvernightExposureService {

    private static final Logger log = LoggerFactory.getLogger(OvernightExposureService.class);

    private final PortfolioDataProvider portfolioDataProvider;
    private final PortfolioAlertRepository portfolioAlertRepository;
    private final AccountConfig config;

    public OvernightExposureService(PortfolioDataProvider portfolioDataProvider,
                                    PortfolioAlertRepository portfolioAlertRepository,
                                    AccountConfig config) {
        this.portfolioDataProvider = portfolioDataProvider;
        this.portfolioAlertRepository = portfolioAlertRepository;
        this.config = config;
    }

    public void checkOvernightExposure() {
        PortfolioSnapshot snapshot = portfolioDataProvider.fetchSnapshot();
        BigDecimal accountValue = snapshot.accountValue();
        if (accountValue == null || accountValue.signum() <= 0) {
            log.warn("Övernattskontroll hoppades över - kontovärdet saknas eller är noll");
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        for (PortfolioHolding holding : snapshot.holdings()) {
            checkHolding(holding, accountValue, today);
        }
    }

    private void checkHolding(PortfolioHolding holding, BigDecimal accountValue, LocalDate today) {
        if (holding.marketValue() == null || holding.currentPrice() == null || holding.isin() == null) {
            log.warn("Övernattskontroll: {} saknar värde, kurs eller ISIN - hoppas över", holding.name());
            return;
        }

        BigDecimal weight = holding.marketValue().divide(accountValue, 4, RoundingMode.HALF_UP);
        if (weight.compareTo(config.maxOvernightPositionPercent()) <= 0
                || portfolioAlertRepository.existsByIsinAndAlertDate(holding.isin(), today)) {
            return;
        }

        // Avrundas UPPÅT så positionen hamnar på eller under målvikten
        BigDecimal targetValue = accountValue.multiply(config.overnightTrimTargetPercent());
        int sharesToSell = holding.marketValue().subtract(targetValue)
                .divide(holding.currentPrice(), 0, RoundingMode.UP).intValue();

        PortfolioAlert alert = new PortfolioAlert();
        alert.setIsin(holding.isin());
        alert.setName(holding.name());
        alert.setWeightPercent(weight.multiply(BigDecimal.valueOf(100)));
        alert.setSuggestedSharesToSell(sharesToSell);
        alert.setAlertDate(today);
        portfolioAlertRepository.save(alert);
    }
}
