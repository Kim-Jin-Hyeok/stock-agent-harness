package com.stock.strategy.universe.candidate.evaluation.runner;

import com.stock.strategy.universe.candidate.evaluation.query.StockCandidateEvaluationQueryService;
import com.stock.strategy.universe.candidate.evaluation.request.StockCandidateEvaluationRequest;
import com.stock.strategy.universe.candidate.evaluation.runner.config.StockCandidateEvaluationProperties;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.candidate.evaluation.snapshot.storage.StockCandidateEvaluationSnapshotStore;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.stream.Stream;

import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.disabledProperties;
import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.properties;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.allIneligibleSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.allMissingSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.belowMinimumSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.eligibilityIncompleteSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.liquidityIncompleteSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class StockCandidateEvaluationRunnerTest {
    private static final String START_LOG = "Stock candidate evaluation started.";
    private static final String RECORDED_LOG = "Stock candidate evaluation recorded.";
    private final StockCandidateEvaluationQueryService queryService = mock(StockCandidateEvaluationQueryService.class);
    private final StockCandidateEvaluationSnapshotStore snapshotStore = mock(StockCandidateEvaluationSnapshotStore.class);

    @Test
    void doesNotEvaluateSaveOrLogWhenDisabled(CapturedOutput output) {
        runner(disabledProperties()).run(new DefaultApplicationArguments());

        verifyNoInteractions(queryService, snapshotStore);
        assertThat(output).doesNotContain(START_LOG, RECORDED_LOG);
    }

    @ParameterizedTest(name = "case {index}")
    @MethodSource("recordableEvaluations")
    void evaluatesThenSavesEveryOutcomeExactlyOnceAndLogsSeparateStageCounts(
            StockCandidateEvaluationSnapshot snapshot, int eligibleCount, int excludedCount,
            int eligibilityUnverifiedCount, boolean liquidityEvaluated, int liquidityUnverifiedCount,
            int selectedCount, CapturedOutput output
    ) {
        var properties = properties(snapshot);
        var request = properties.toRequest();
        when(queryService.evaluate(request, properties.eligibilityInputs())).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(17L);

        runner(properties).run(new DefaultApplicationArguments());

        var order = inOrder(queryService, snapshotStore);
        order.verify(queryService).evaluate(request, properties.eligibilityInputs());
        order.verify(snapshotStore).save(snapshot);
        order.verifyNoMoreInteractions();
        assertThat(output).contains(START_LOG, "request=" + request, RECORDED_LOG,
                "snapshotId=17", "status=" + snapshot.evaluationResult().status(),
                "selectionAsOfDate=2026-09-23", "targetCount=" + request.targetSymbols().size(),
                "eligibleCount=" + eligibleCount, "excludedCount=" + excludedCount,
                "eligibilityUnverifiedCount=" + eligibilityUnverifiedCount, "liquidityEvaluated=" + liquidityEvaluated,
                "liquidityUnverifiedCount=" + liquidityUnverifiedCount, "selectedCount=" + selectedCount);
        assertThat(output).doesNotContain("inputHistories=", "eligibilityInputs=", "sourceReference=",
                "synthetic-source-01", "snapshotJson=", "appKey=", "appSecret=");
    }

    @Test
    void propagatesQueryFailureWithoutSavingRetryOrRecordedLog(CapturedOutput output) {
        var properties = properties(completeSnapshot());
        var failure = new IllegalStateException("Evaluation failed.");
        when(queryService.evaluate(properties.toRequest(), properties.eligibilityInputs())).thenThrow(failure);

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(queryService).evaluate(properties.toRequest(), properties.eligibilityInputs());
        verifyNoMoreInteractions(queryService);
        verifyNoInteractions(snapshotStore);
        assertThat(output).contains(START_LOG).doesNotContain(RECORDED_LOG);
    }

    @Test
    void propagatesSaveFailureWithoutRetryOrRecordedLog(CapturedOutput output) {
        var snapshot = completeSnapshot();
        var properties = properties(snapshot);
        var failure = new IllegalStateException("Database unavailable.");
        when(queryService.evaluate(properties.toRequest(), properties.eligibilityInputs())).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenThrow(failure);

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(queryService).evaluate(properties.toRequest(), properties.eligibilityInputs());
        verify(snapshotStore).save(snapshot);
        verifyNoMoreInteractions(queryService, snapshotStore);
        assertThat(output).contains(START_LOG).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsNullEvaluationResponseBeforeSaving(CapturedOutput output) {
        var properties = properties(completeSnapshot());

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationResult must not be null.");
        verify(queryService).evaluate(properties.toRequest(), properties.eligibilityInputs());
        verifyNoMoreInteractions(queryService);
        verifyNoInteractions(snapshotStore);
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsEvaluationForDifferentCriteriaBeforeSaving(CapturedOutput output) {
        var snapshot = completeSnapshot();
        var original = snapshot.evaluationResult().request();
        var liquidity = original.liquidityRequest();
        var differentRequest = new StockCandidateEvaluationRequest(original.eligibilityRequest(),
                new DailyTradingValueSelectionEvaluationRequest(liquidity.targetSymbols(), liquidity.selectionAsOfDate(),
                        liquidity.requiredTradingDates(), liquidity.expectedVenueScope(), 999L, liquidity.maxCandidateCount()));
        var properties = properties(differentRequest, properties(snapshot).eligibilityInputs());
        when(queryService.evaluate(differentRequest, properties.eligibilityInputs())).thenReturn(snapshot.evaluationResult());

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation result request must match manual evaluation request.");
        verifyNoInteractions(snapshotStore);
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsNonPositiveSavedIdWithoutRecordedLog(long id, CapturedOutput output) {
        var snapshot = completeSnapshot();
        var properties = properties(snapshot);
        when(queryService.evaluate(properties.toRequest(), properties.eligibilityInputs())).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(id);

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Saved snapshotId must be positive.");
        verify(snapshotStore).save(snapshot);
        verifyNoMoreInteractions(snapshotStore);
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsMissingSavedIdWithoutRecordedLog(CapturedOutput output) {
        var snapshot = completeSnapshot();
        var properties = properties(snapshot);
        when(queryService.evaluate(properties.toRequest(), properties.eligibilityInputs())).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(null);

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshotId must not be null.");
        verify(snapshotStore).save(snapshot);
        verifyNoMoreInteractions(snapshotStore);
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsNullDependencies() {
        var properties = properties(completeSnapshot());
        assertThatThrownBy(() -> new StockCandidateEvaluationRunner(null, snapshotStore, properties))
                .isInstanceOf(NullPointerException.class).hasMessage("queryService must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationRunner(queryService, null, properties))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshotStore must not be null.");
        assertThatThrownBy(() -> new StockCandidateEvaluationRunner(queryService, snapshotStore, null))
                .isInstanceOf(NullPointerException.class).hasMessage("properties must not be null.");
    }

    private StockCandidateEvaluationRunner runner(StockCandidateEvaluationProperties properties) {
        return new StockCandidateEvaluationRunner(queryService, snapshotStore, properties);
    }

    private static Stream<Arguments> recordableEvaluations() {
        return Stream.of(
                Arguments.of(completeSnapshot(), 2, 1, 0, true, 0, 2),
                Arguments.of(eligibilityIncompleteSnapshot(), 1, 0, 1, false, 0, 0),
                Arguments.of(liquidityIncompleteSnapshot(), 2, 0, 0, true, 2, 0),
                Arguments.of(allIneligibleSnapshot(), 0, 1, 0, false, 0, 0),
                Arguments.of(belowMinimumSnapshot(), 1, 0, 0, true, 0, 0),
                Arguments.of(allMissingSnapshot(), 0, 0, 2, false, 0, 0)
        );
    }
}
