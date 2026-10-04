package com.stock.strategy.universe.candidate.evaluation.snapshot;

import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;

import java.util.Objects;

public record StockCandidateEvaluationSnapshot(
        int schemaVersion,
        StockCandidateEvaluationResult evaluationResult
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public StockCandidateEvaluationSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported snapshot schemaVersion: " + schemaVersion);
        }
        Objects.requireNonNull(evaluationResult, "evaluationResult must not be null.");
    }

    public static StockCandidateEvaluationSnapshot from(StockCandidateEvaluationResult evaluationResult) {
        return new StockCandidateEvaluationSnapshot(CURRENT_SCHEMA_VERSION, evaluationResult);
    }
}
