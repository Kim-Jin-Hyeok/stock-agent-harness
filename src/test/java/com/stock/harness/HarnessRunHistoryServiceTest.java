package com.stock.harness;

import com.stock.harness.persistence.HarnessRunEntity;
import com.stock.harness.persistence.HarnessRunRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HarnessRunHistoryServiceTest {
    private final HarnessRunRepository harnessRunRepository = mock(HarnessRunRepository.class);
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService(harnessRunRepository);

    @Test
    void recordStoresCompleteRunResult() {
        harnessRunHistoryService.record(completedRun("run-1"));

        assertThat(harnessRunHistoryService.getRunById("run-1")).isPresent();

        verify(harnessRunRepository).save(any());
    }

    @Test
    void recordStoresFailRunResult() {
        harnessRunHistoryService.record(failedRun("run-1"));

        Optional<HarnessRunResult> result = harnessRunHistoryService.getRunById("run-1");

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(HarnessRunStatus.FAILED);

        verify(harnessRunRepository).save(any());
    }

    @Test
    void getRunByIdReturnsMatchingRun() {
        harnessRunHistoryService.record(completedRun("run-1"));
        harnessRunHistoryService.record(completedRun("run-2"));

        Optional<HarnessRunResult> result = harnessRunHistoryService.getRunById("run-2");

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().runId()).isEqualTo("run-2");
    }

    @Test
    void getRunSummariesReturnsRunMetadata() {
        when(harnessRunRepository.findAll())
                .thenReturn(List.of(completedRunEntity("run-1")));

        List<HarnessRunSummary> summaries = harnessRunHistoryService.getRunSummaries();
        verify(harnessRunRepository).findAll();
        assertThat(summaries).hasSize(1);

        HarnessRunSummary summary = summaries.getFirst();
        assertThat(summary.runId()).isEqualTo("run-1");
        assertThat(summary.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(summary.startedAt()).isEqualTo(startedAt());
        assertThat(summary.finishedAt()).isEqualTo(finishedAt());
    }

    @Test
    void clearRemovesRunHistory() {
        harnessRunHistoryService.record(failedRun("run-1"));

        harnessRunHistoryService.clear();

        assertThat(harnessRunHistoryService.getRunById("run-1")).isEmpty();
        verify(harnessRunRepository).deleteAll();
    }

    private HarnessRunResult completedRun(String runId) {
        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(completedStep()),
                null,
                null,
                null,
                null,
                null
        );
    }

    private HarnessRunResult failedRun(String runId) {
        return HarnessRunResult.failed(
                runId,
                startedAt(),
                finishedAt(),
                List.of(failedStep())
        );
    }

    private HarnessRunEntity completedRunEntity(String runId) {
        return HarnessRunEntity.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt()
        );
    }

    private LocalDateTime startedAt() {
        return LocalDateTime.of(2026, 1, 1, 9, 0);
    }

    private LocalDateTime finishedAt() {
        return startedAt().plusSeconds(1);
    }

    private HarnessStepResult completedStep() {
        return new HarnessStepResult(
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepStatus.COMPLETED,
                "Test completed step."
        );
    }

    private HarnessStepResult failedStep() {
        return new HarnessStepResult(
                HarnessStepType.RUN_FAILED,
                HarnessStepStatus.FAILED,
                "Test failed step."
        );
    }
}
