package com.qullamaggie.tradingsystem.indicators;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;

import java.util.List;

/**
 * The two phases of a high tight flag: the run-up (flagpole) and the
 * consolidation (flag) that follows its peak.
 */
public record FlagPhases(List<DailyPrice> flagpole, List<DailyPrice> flag) {}