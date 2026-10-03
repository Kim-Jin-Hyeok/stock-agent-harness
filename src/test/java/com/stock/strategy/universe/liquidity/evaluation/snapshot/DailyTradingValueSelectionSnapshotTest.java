package com.stock.strategy.universe.liquidity.evaluation.snapshot;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationResult;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyTradingValueSelectionSnapshotTest {
    @Test
    void wrapsExistingEvaluationResultWithCurrentFormatVersion() {
        DailyTradingValueSelectionEvaluationResult result = incompleteResult();

        DailyTradingValueSelectionSnapshot snapshot = DailyTradingValueSelectionSnapshot.from(result);

        assertThat(snapshot.schemaVersion()).isEqualTo(1);
        assertThat(snapshot.evaluationResult()).isSameAs(result);
        assertThat(snapshot).isEqualTo(new DailyTradingValueSelectionSnapshot(1, result));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2, Integer.MAX_VALUE})
    void rejectsUnsupportedSchemaVersion(int version) {
        assertThatThrownBy(() -> new DailyTradingValueSelectionSnapshot(version, incompleteResult()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported snapshot schemaVersion: " + version);
    }

    @Test
    void rejectsNullEvaluationResult() {
        assertThatThrownBy(() -> new DailyTradingValueSelectionSnapshot(1, null))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationResult must not be null.");
        assertThatThrownBy(() -> DailyTradingValueSelectionSnapshot.from(null))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationResult must not be null.");
    }

    private DailyTradingValueSelectionEvaluationResult incompleteResult() {
        LocalDate date = LocalDate.of(2026, 9, 23);
        return new DailyTradingValueSelectionEvaluationResult(
                new DailyTradingValueSelectionEvaluationRequest(
                        List.of("005930"), date, List.of(date), TradingVenueScope.INTEGRATED, 100L, 1
                ),
                DailyTradingValueSelectionEvaluationStatus.INCOMPLETE,
                List.of(), List.of(), List.of("005930"), List.of()
        );
    }
}
