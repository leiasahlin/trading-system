package com.qullamaggie.tradingsystem.portfolio.controller;

import com.qullamaggie.tradingsystem.data.entity.PortfolioAlert;
import com.qullamaggie.tradingsystem.data.repository.PortfolioAlertRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of portfolio-level warnings, e.g. overnight exposure. */
@RestController
@RequestMapping("/api/portfolio-alerts")
public class PortfolioAlertController {

    private final PortfolioAlertRepository portfolioAlertRepository;

    public PortfolioAlertController(PortfolioAlertRepository portfolioAlertRepository) {
        this.portfolioAlertRepository = portfolioAlertRepository;
    }

    @GetMapping
    public List<PortfolioAlert> getAll() {
        return portfolioAlertRepository.findAll();
    }
}
