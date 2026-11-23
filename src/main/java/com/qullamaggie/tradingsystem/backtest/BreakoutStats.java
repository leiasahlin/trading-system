package com.qullamaggie.tradingsystem.backtest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects judged days into per-year counters, and keeps the
 * signals and near misses for the detailed CSV.
 */
public class BreakoutStats {

    private final Map<Integer, YearStats> byYear = new TreeMap<>();
    private final List<BreakoutDay> interestingDays = new ArrayList<>();

    public void record(BreakoutDay day) {
        int year = day.date().getYear();
        byYear.computeIfAbsent(year, y -> new YearStats()).record(day);

        if (day.isSignal() || day.isNearMiss()) {
            interestingDays.add(day);
        }
    }

    public void recordAll(List<BreakoutDay> days) {
        for (BreakoutDay day : days) {
            record(day);
        }
    }

    public Map<Integer, YearStats> byYear() {
        return byYear;
    }

    public List<BreakoutDay> interestingDays() {
        return interestingDays;
    }
}