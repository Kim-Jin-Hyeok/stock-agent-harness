package com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence;

import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
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
@Table(name = "daily_trading_value_selection_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyTradingValueSelectionSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "selection_as_of_date", nullable = false, updatable = false)
    private LocalDate selectionAsOfDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, updatable = false, length = 20)
    private DailyTradingValueSelectionEvaluationStatus evaluationStatus;

    @Lob
    @Column(name = "snapshot_json", nullable = false, updatable = false, length = Integer.MAX_VALUE)
    private String snapshotJson;

    public static DailyTradingValueSelectionSnapshotEntity from(
            DailyTradingValueSelectionSnapshot snapshot,
            String snapshotJson,
            Instant recordedAt
    ) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        Objects.requireNonNull(snapshotJson, "snapshotJson must not be null.");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null.");
        if (snapshotJson.isBlank()) {
            throw new IllegalArgumentException("snapshotJson must not be blank.");
        }

        DailyTradingValueSelectionSnapshotEntity entity = new DailyTradingValueSelectionSnapshotEntity();
        entity.recordedAt = recordedAt;
        entity.selectionAsOfDate = snapshot.evaluationResult().request().selectionAsOfDate();
        entity.evaluationStatus = snapshot.evaluationResult().status();
        entity.snapshotJson = snapshotJson;
        return entity;
    }
}
