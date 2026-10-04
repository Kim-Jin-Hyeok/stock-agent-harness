package com.stock.strategy.universe.candidate.evaluation.snapshot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockCandidateEvaluationSnapshotRepository
        extends JpaRepository<StockCandidateEvaluationSnapshotEntity, Long> {
}
