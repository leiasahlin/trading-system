package com.qullamaggie.tradingsystem.scanner;

import java.util.Set;

public record BreakoutEvaluation(Set<BreakoutCriterion> failed) {

    public boolean allPassed() {
        return failed.isEmpty();
    }

    public int passedCount() {
        return BreakoutCriterion.values().length - failed().size();
    }
}