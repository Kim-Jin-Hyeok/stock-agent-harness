package com.stock.strategy.universe.candidate.evaluation.snapshot.storage;

import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.candidate.evaluation.snapshot.json.StockCandidateEvaluationSnapshotJsonConverter;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotEntity;
import com.stock.strategy.universe.candidate.evaluation.snapshot.persistence.StockCandidateEvaluationSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
public class StockCandidateEvaluationSnapshotStore {
    private final StockCandidateEvaluationSnapshotRepository repository;
    private final StockCandidateEvaluationSnapshotJsonConverter converter;

    public StockCandidateEvaluationSnapshotStore(
            StockCandidateEvaluationSnapshotRepository repository,
            StockCandidateEvaluationSnapshotJsonConverter converter
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null.");
        this.converter = Objects.requireNonNull(converter, "converter must not be null.");
    }

    @Transactional
    public Long save(StockCandidateEvaluationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        String json = converter.toJson(snapshot);
        // Preserve each evaluation, including earlier incomplete evidence, as a new row.
        StockCandidateEvaluationSnapshotEntity entity = StockCandidateEvaluationSnapshotEntity.from(
                snapshot, json, Instant.now()
        );
        return repository.saveAndFlush(entity).getId();
    }

    @Transactional(readOnly = true)
    public Optional<StockCandidateEvaluationSnapshot> findById(Long id) {
        Objects.requireNonNull(id, "id must not be null.");
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive.");
        }
        return repository.findById(id).map(this::restore);
    }

    private StockCandidateEvaluationSnapshot restore(StockCandidateEvaluationSnapshotEntity entity) {
        StockCandidateEvaluationSnapshot snapshot = converter.fromJson(entity.getSnapshotJson());
        if (!snapshot.evaluationResult().request().eligibilityRequest().selectionAsOfDate()
                .equals(entity.getSelectionAsOfDate())
                || snapshot.evaluationResult().status() != entity.getEvaluationStatus()) {
            throw new IllegalStateException(
                    "Stored stock candidate evaluation snapshot metadata does not match payload. id=" + entity.getId()
            );
        }
        return snapshot;
    }
}
