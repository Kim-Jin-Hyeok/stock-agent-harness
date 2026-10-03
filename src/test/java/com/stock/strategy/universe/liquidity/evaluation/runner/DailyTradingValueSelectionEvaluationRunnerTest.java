package com.stock.strategy.universe.liquidity.evaluation.runner;

import com.stock.strategy.universe.liquidity.evaluation.query.DailyTradingValueSelectionQueryService;
import com.stock.strategy.universe.liquidity.evaluation.request.DailyTradingValueSelectionEvaluationRequest;
import com.stock.strategy.universe.liquidity.evaluation.result.DailyTradingValueSelectionEvaluationStatus;
import com.stock.strategy.universe.liquidity.evaluation.runner.config.DailyTradingValueSelectionEvaluationProperties;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.DailyTradingValueSelectionSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.evaluate;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.incompleteSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DailyTradingValueSelectionEvaluationRunnerTest {
    private static final String START_LOG = "Daily trading value selection evaluation started.";
    private static final String RECORDED_LOG = "Daily trading value selection evaluation recorded.";
    private final DailyTradingValueSelectionQueryService queryService = mock(DailyTradingValueSelectionQueryService.class);
    private final DailyTradingValueSelectionSnapshotStore snapshotStore = mock(DailyTradingValueSelectionSnapshotStore.class);

    @Test
    void doesNotEvaluateSaveOrLogWhenDisabled(CapturedOutput output) {
        var disabled = new DailyTradingValueSelectionEvaluationProperties(false, null, null, null, null, null, null);

        runner(disabled).run(new DefaultApplicationArguments());

        verifyNoInteractions(queryService, snapshotStore);
        assertThat(output).doesNotContain(START_LOG, RECORDED_LOG);
    }

    @ParameterizedTest
    @EnumSource(DailyTradingValueSelectionEvaluationStatus.class)
    void evaluatesThenSavesExactlyOnceAndLogsActualStateAndCounts(
            DailyTradingValueSelectionEvaluationStatus status, CapturedOutput output
    ) {
        DailyTradingValueSelectionSnapshot snapshot = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE
                ? completeSnapshot() : incompleteSnapshot();
        var request = snapshot.evaluationResult().request();
        when(queryService.evaluate(request)).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(17L);

        runner(properties(request)).run(new DefaultApplicationArguments());

        var ordered = inOrder(queryService, snapshotStore);
        ordered.verify(queryService).evaluate(request);
        ordered.verify(snapshotStore).save(snapshot);
        ordered.verifyNoMoreInteractions();
        boolean complete = status == DailyTradingValueSelectionEvaluationStatus.COMPLETE;
        assertThat(output).contains(START_LOG, "request=" + request, RECORDED_LOG, "snapshotId=17", "status=" + status,
                "selectionAsOfDate=2026-09-23", "targetCount=4", "calculatedCount=" + (complete ? 4 : 1),
                "selectedCount=" + (complete ? 2 : 0), "unverifiedCount=" + (complete ? 0 : 3));
        assertThat(output).doesNotContain("inputHistories=", "appKey=", "appSecret=", "snapshotJson=");
    }

    @Test
    void savesCompleteEvaluationEvenWhenNoCandidateMeetsMinimum(CapturedOutput output) {
        var original = completeSnapshot().evaluationResult();
        var request = new DailyTradingValueSelectionEvaluationRequest(
                original.request().targetSymbols(), original.request().selectionAsOfDate(),
                original.request().requiredTradingDates(), original.request().expectedVenueScope(), 1_000L, 2
        );
        var snapshot = evaluate(request, original.inputHistories());
        when(queryService.evaluate(request)).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(18L);

        runner(properties(request)).run(new DefaultApplicationArguments());

        verify(snapshotStore).save(snapshot);
        assertThat(output).contains(RECORDED_LOG, "snapshotId=18", "status=COMPLETE", "selectedCount=0");
    }

    @Test
    void propagatesQueryOrEvaluationFailureWithoutSavingRetryOrRecordedLog(CapturedOutput output) {
        var request = completeSnapshot().evaluationResult().request();
        var failure = new IllegalStateException("Evaluation failed.");
        when(queryService.evaluate(request)).thenThrow(failure);

        assertThatThrownBy(() -> runner(properties(request)).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(queryService).evaluate(request);
        verifyNoMoreInteractions(queryService);
        verifyNoInteractions(snapshotStore);
        assertThat(output).contains(START_LOG).doesNotContain(RECORDED_LOG);
    }

    @Test
    void propagatesSaveFailureWithoutRetryOrRecordedLog(CapturedOutput output) {
        var snapshot = completeSnapshot();
        var request = snapshot.evaluationResult().request();
        var failure = new IllegalStateException("Database unavailable.");
        when(queryService.evaluate(request)).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenThrow(failure);

        assertThatThrownBy(() -> runner(properties(request)).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(queryService).evaluate(request);
        verify(snapshotStore).save(snapshot);
        verifyNoMoreInteractions(queryService, snapshotStore);
        assertThat(output).contains(START_LOG).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsNullEvaluationResultBeforeSaving(CapturedOutput output) {
        var request = completeSnapshot().evaluationResult().request();

        assertThatThrownBy(() -> runner(properties(request)).run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class).hasMessage("evaluationResult must not be null.");
        verifyNoInteractions(snapshotStore);
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsResultForDifferentCriteriaBeforeSaving(CapturedOutput output) {
        var snapshot = completeSnapshot();
        var original = snapshot.evaluationResult().request();
        var differentRequest = new DailyTradingValueSelectionEvaluationRequest(
                original.targetSymbols(), original.selectionAsOfDate(), original.requiredTradingDates(),
                original.expectedVenueScope(), 999L, original.maxCandidateCount()
        );
        when(queryService.evaluate(differentRequest)).thenReturn(snapshot.evaluationResult());

        assertThatThrownBy(() -> runner(properties(differentRequest)).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Evaluation result request must match manual evaluation request.");
        verifyNoInteractions(snapshotStore);
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsInvalidSavedIdWithoutRecordedLog(long id, CapturedOutput output) {
        var snapshot = completeSnapshot();
        var request = snapshot.evaluationResult().request();
        when(queryService.evaluate(request)).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(id);

        assertThatThrownBy(() -> runner(properties(request)).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class).hasMessage("Saved snapshotId must be positive.");
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsMissingSavedIdWithoutRecordedLog(CapturedOutput output) {
        var snapshot = completeSnapshot();
        var request = snapshot.evaluationResult().request();
        when(queryService.evaluate(request)).thenReturn(snapshot.evaluationResult());
        when(snapshotStore.save(snapshot)).thenReturn(null);

        assertThatThrownBy(() -> runner(properties(request)).run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshotId must not be null.");
        assertThat(output).doesNotContain(RECORDED_LOG);
    }

    @Test
    void rejectsNullDependencies() {
        var properties = properties(completeSnapshot().evaluationResult().request());
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRunner(null, snapshotStore, properties))
                .isInstanceOf(NullPointerException.class).hasMessage("queryService must not be null.");
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRunner(queryService, null, properties))
                .isInstanceOf(NullPointerException.class).hasMessage("snapshotStore must not be null.");
        assertThatThrownBy(() -> new DailyTradingValueSelectionEvaluationRunner(queryService, snapshotStore, null))
                .isInstanceOf(NullPointerException.class).hasMessage("properties must not be null.");
    }

    private DailyTradingValueSelectionEvaluationRunner runner(DailyTradingValueSelectionEvaluationProperties properties) {
        return new DailyTradingValueSelectionEvaluationRunner(queryService, snapshotStore, properties);
    }

    private DailyTradingValueSelectionEvaluationProperties properties(DailyTradingValueSelectionEvaluationRequest request) {
        return new DailyTradingValueSelectionEvaluationProperties(
                true, request.targetSymbols(), request.selectionAsOfDate(), request.requiredTradingDates(),
                request.expectedVenueScope(), request.minimumAverageTradingValueKrw(), request.maxCandidateCount()
        );
    }
}
