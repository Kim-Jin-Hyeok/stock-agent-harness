package com.stock.harness;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessRunHistoryServiceTest {
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService();

    @Test
    void recordStoresCompleteRunResult() {
        harnessRunHistoryService.record(completedRun("run-1"));

        assertThat(harnessRunHistoryService.getRuns()).hasSize(1);
        assertThat(harnessRunHistoryService.getRuns().getFirst().runId()).isEqualTo("run-1");
    }

    @Test
    void recordStoresFailRunResult() {
        harnessRunHistoryService.record(failedRun("run-1"));

        assertThat(harnessRunHistoryService.getRuns()).hasSize(1);
        assertThat(harnessRunHistoryService.getRuns().getFirst().runId()).isEqualTo("run-1");
        assertThat(harnessRunHistoryService.getRuns().getFirst().status()).isEqualTo(HarnessRunStatus.FAILED);
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
    void clearRemovesRunHistory() {
        harnessRunHistoryService.record(failedRun("run-1"));

        harnessRunHistoryService.clear();

        assertThat(harnessRunHistoryService.getRuns()).isEmpty();
    }

    private HarnessRunResult completedRun(String runId) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt,
                List.of(completedStep()),
                null,
                null,
                null,
                null,
                null
        );
    }

    private HarnessRunResult failedRun(String runId) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return HarnessRunResult.failed(
                runId,
                startedAt,
                finishedAt,
                List.of(failedStep())
        );
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
