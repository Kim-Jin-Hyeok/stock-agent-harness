package com.stock.strategy.universe.candidate.evaluation.snapshot.persistence;

import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCandidateEvaluationSnapshotEntityTest {
    private static final Instant RECORDED_AT = Instant.parse("2026-10-04T09:00:00Z");

    @ParameterizedTest(name = "case {index}")
    @MethodSource("com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture#snapshots")
    void derivesMetadataFromAnyEvaluationWithoutAssigningExistingId(StockCandidateEvaluationSnapshot snapshot) {
        StockCandidateEvaluationSnapshotEntity entity = StockCandidateEvaluationSnapshotEntity.from(
                snapshot, "serialized-snapshot", RECORDED_AT);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getSelectionAsOfDate()).isEqualTo(DATE);
        assertThat(entity.getEvaluationStatus()).isEqualTo(snapshot.evaluationResult().status());
        assertThat(entity.getRecordedAt()).isEqualTo(RECORDED_AT);
        assertThat(entity.getSnapshotJson()).isEqualTo("serialized-snapshot");
        assertThat(entity.getRecordedAt()).isNotEqualTo(snapshot.evaluationResult().request().eligibilityRequest().selectionCutoffAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void rejectsBlankJson(String json) {
        assertThatThrownBy(() -> StockCandidateEvaluationSnapshotEntity.from(completeSnapshot(), json, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("snapshotJson must not be blank.");
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> StockCandidateEvaluationSnapshotEntity.from(null, "{}", RECORDED_AT))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshot must not be null.");
        assertThatThrownBy(() -> StockCandidateEvaluationSnapshotEntity.from(completeSnapshot(), null, RECORDED_AT))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshotJson must not be null.");
        assertThatThrownBy(() -> StockCandidateEvaluationSnapshotEntity.from(completeSnapshot(), "{}", null))
                .isInstanceOf(NullPointerException.class).hasMessage("recordedAt must not be null.");
    }
}
