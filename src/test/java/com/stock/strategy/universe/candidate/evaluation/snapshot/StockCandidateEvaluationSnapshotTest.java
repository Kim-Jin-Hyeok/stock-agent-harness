package com.stock.strategy.universe.candidate.evaluation.snapshot;

import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCandidateEvaluationSnapshotTest {
    @Test
    void wrapsExistingResultWithCurrentFormatVersionWithoutCopyingOrDiscardingEvidence() {
        StockCandidateEvaluationResult result = incompleteResult();

        StockCandidateEvaluationSnapshot snapshot = StockCandidateEvaluationSnapshot.from(result);

        assertThat(snapshot.schemaVersion()).isEqualTo(1);
        assertThat(snapshot.evaluationResult()).isSameAs(result);
        assertThat(snapshot).isEqualTo(new StockCandidateEvaluationSnapshot(1, result));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, Integer.MAX_VALUE})
    void rejectsUnsupportedSchemaVersion(int version) {
        assertThatThrownBy(() -> new StockCandidateEvaluationSnapshot(version, incompleteResult()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported snapshot schemaVersion: " + version);
    }

    @Test
    void rejectsNullEvaluationResult() {
        assertThatThrownBy(() -> new StockCandidateEvaluationSnapshot(1, null))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationResult must not be null.");
        assertThatThrownBy(() -> StockCandidateEvaluationSnapshot.from(null))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationResult must not be null.");
    }

    private StockCandidateEvaluationResult incompleteResult() {
        return service().evaluate(request("005930"), List.of(), List.of());
    }
}
