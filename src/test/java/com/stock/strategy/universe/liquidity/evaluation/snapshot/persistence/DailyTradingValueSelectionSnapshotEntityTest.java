package com.stock.strategy.universe.liquidity.evaluation.snapshot.persistence;

import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.SELECTION_DATE;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.incompleteSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionSnapshotEntityTest {
    private static final Instant RECORDED_AT = Instant.parse("2026-10-03T09:00:00Z");

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void derivesMetadataFromSnapshotWithoutAssigningAnExistingId(DailyTradingValueSelectionEvaluationStatus status) {
        DailyTradingValueSelectionSnapshot snapshot = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();

        DailyTradingValueSelectionSnapshotEntity entity = DailyTradingValueSelectionSnapshotEntity.from(
                snapshot, "{\"schemaVersion\":1}", RECORDED_AT
        );

        assertThat(entity.getId()).isNull();
        assertThat(entity.getSelectionAsOfDate()).isEqualTo(SELECTION_DATE);
        assertThat(entity.getEvaluationStatus()).isEqualTo(status);
        assertThat(entity.getRecordedAt()).isEqualTo(RECORDED_AT);
        assertThat(entity.getSnapshotJson()).isEqualTo("{\"schemaVersion\":1}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void rejectsBlankJson(String json) {
        assertThatThrownBy(() -> DailyTradingValueSelectionSnapshotEntity.from(
                completeSnapshot(), json, RECORDED_AT
        )).isInstanceOf(IllegalArgumentException.class).hasMessage("snapshotJson must not be blank.");
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> DailyTradingValueSelectionSnapshotEntity.from(null, "{}", RECORDED_AT))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshot must not be null.");
        assertThatThrownBy(() -> DailyTradingValueSelectionSnapshotEntity.from(completeSnapshot(), null, RECORDED_AT))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshotJson must not be null.");
        assertThatThrownBy(() -> DailyTradingValueSelectionSnapshotEntity.from(completeSnapshot(), "{}", null))
                .isInstanceOf(NullPointerException.class).hasMessage("recordedAt must not be null.");
    }
}
