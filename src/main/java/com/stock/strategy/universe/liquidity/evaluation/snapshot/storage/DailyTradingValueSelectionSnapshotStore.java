package com.stock.strategy.universe.liquidity.evaluation.snapshot.storage;

import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.json.DailyTradingValueSelectionSnapshotJsonConverter;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotEntity;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence.DailyTradingValueSelectionSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
public class DailyTradingValueSelectionSnapshotStore {
    private final DailyTradingValueSelectionSnapshotRepository repository;
    private final DailyTradingValueSelectionSnapshotJsonConverter converter;

    public DailyTradingValueSelectionSnapshotStore(
            DailyTradingValueSelectionSnapshotRepository repository,
            DailyTradingValueSelectionSnapshotJsonConverter converter
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null.");
        this.converter = Objects.requireNonNull(converter, "converter must not be null.");
    }

    @Transactional
    public Long save(DailyTradingValueSelectionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null.");
        String json = converter.toJson(snapshot);
        // Always insert a new evaluation; a later completion must not replace earlier evidence.
        DailyTradingValueSelectionSnapshotEntity entity = DailyTradingValueSelectionSnapshotEntity.from(
                snapshot, json, Instant.now()
        );
        return repository.saveAndFlush(entity).getId();
    }

    @Transactional(readOnly = true)
    public Optional<DailyTradingValueSelectionSnapshot> findById(Long id) {
        Objects.requireNonNull(id, "id must not be null.");
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive.");
        }
        return repository.findById(id).map(this::restore);
    }

    private DailyTradingValueSelectionSnapshot restore(DailyTradingValueSelectionSnapshotEntity entity) {
        DailyTradingValueSelectionSnapshot snapshot = converter.fromJson(entity.getSnapshotJson());
        if (!snapshot.evaluationResult().request().selectionAsOfDate().equals(entity.getSelectionAsOfDate())
                || snapshot.evaluationResult().status() != entity.getEvaluationStatus()) {
            throw new IllegalStateException(
                    "Stored daily trading value selection snapshot metadata does not match payload. id="
                            + entity.getId()
            );
        }
        return snapshot;
    }
}
