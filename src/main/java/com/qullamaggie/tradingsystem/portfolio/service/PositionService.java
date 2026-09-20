package com.qullamaggie.tradingsystem.portfolio.service;

import com.qullamaggie.tradingsystem.data.entity.*;
import com.qullamaggie.tradingsystem.data.repository.*;
import com.qullamaggie.tradingsystem.portfolio.PositionSummary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final PositionSummaryProvider positionSummaryProvider;

    public PositionService(PositionRepository positionRepository,
                           PositionSummaryProvider positionSummaryProvider) {
        this.positionRepository = positionRepository;
        this.positionSummaryProvider = positionSummaryProvider;
    }

    public Optional<PositionSummary> getSummary(Long positionId) {
        return positionRepository.findById(positionId)
                .map(positionSummaryProvider::buildSummary);
    }

    public List<PositionSummary> getSummariesForAllOpenPositions() {
        List<Position> openPositions = positionRepository.findByStatus(PositionStatus.OPEN);
        return openPositions.stream()
                .map(positionSummaryProvider::buildSummary)
                .toList();
    }
}
