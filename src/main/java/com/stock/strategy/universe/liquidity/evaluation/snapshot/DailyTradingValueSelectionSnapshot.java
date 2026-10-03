package com.stock.strategy.universe.liquidity.evaluation.snapshot;

import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;

import java.util.Objects;

public record DailyTradingValueSelectionSnapshot(
        int schemaVersion,
        DailyTradingValueSelectionEvaluationResult evaluationResult
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public DailyTradingValueSelectionSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported snapshot schemaVersion: " + schemaVersion);
        }
        Objects.requireNonNull(evaluationResult, "evaluationResult must not be null.");
    }

    public static DailyTradingValueSelectionSnapshot from(
            DailyTradingValueSelectionEvaluationResult evaluationResult
    ) {
        return new DailyTradingValueSelectionSnapshot(CURRENT_SCHEMA_VERSION, evaluationResult);
    }
}
