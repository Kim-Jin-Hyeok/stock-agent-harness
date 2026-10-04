package com.stock.strategy.universe.candidate.evaluation.snapshot.persistence;

import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationStatus;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "stock_candidate_evaluation_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockCandidateEvaluationSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "selection_as_of_date", nullable = false, updatable = false)
    private LocalDate selectionAsOfDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, updatable = false, length = 20)
    private StockCandidateEvaluationStatus evaluationStatus;

    @Lob
    @Column(name = "snapshot_json", nullable = false, updatable = false, length = Integer.MAX_VALUE)
    private String snapshotJson;

    public static StockCandidateEvaluationSnapshotEntity from(
            StockCandidateEvaluationSnapshot snapshot,
            String snapshotJson,
            Instant recordedAt
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(snapshotJson, "snapshotJson must not be null.");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null.");
        if (snapshotJson.isBlank()) {
            throw new IllegalArgumentException("snapshotJson must not be blank.");
        }

        StockCandidateEvaluationSnapshotEntity entity = new StockCandidateEvaluationSnapshotEntity();
        entity.recordedAt = recordedAt;
        entity.selectionAsOfDate = snapshot.evaluationResult().request().eligibilityRequest().selectionAsOfDate();
        entity.evaluationStatus = snapshot.evaluationResult().status();
        entity.snapshotJson = snapshotJson;
        return entity;
    }
}
