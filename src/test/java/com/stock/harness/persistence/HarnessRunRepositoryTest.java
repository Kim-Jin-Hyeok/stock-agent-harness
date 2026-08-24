package com.stock.harness.persistence;

import com.stock.harness.HarnessRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HarnessRunRepositoryTest {

    @Autowired
    private HarnessRunRepository harnessRunRepository;

    @Test
    void findByRunId() {
        // given
        String runId = "run-1";
        harnessRunRepository.save(completedRunEntity(runId));

        // when
        Optional<HarnessRunEntity> entity = harnessRunRepository.findByRunId(runId);

        // then
        assertThat(entity.isPresent()).isTrue();
        assertThat(entity.get().getRunId()).isEqualTo(runId);
        assertThat(entity.get().getStatus()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(entity.get().getStartedAt()).isEqualTo(startedAt());
        assertThat(entity.get().getFinishedAt()).isEqualTo(finishedAt());
    }

    @Test
    void findByRunIdReturnsEmptyWhenRunDoesNotExist() {
        // when
        Optional<HarnessRunEntity> entity = harnessRunRepository.findByRunId("run-1");

        // then
        assertThat(entity.isEmpty()).isTrue();
    }

    @Test
    void findAllByOrderByStartedAtDescReturnsLatestRunFirst() {
        // given
        String run1 = "run-1";
        LocalDateTime run1StartedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        harnessRunRepository.save(completedRunEntity(run1, run1StartedAt));

        String run2 = "run-2";
        LocalDateTime run2StartedAt = LocalDateTime.of(2026, 1, 1, 9, 10);
        harnessRunRepository.save(completedRunEntity(run2, run2StartedAt));

        List<HarnessRunEntity> entities = harnessRunRepository.findAllByOrderByStartedAtDesc();

        assertThat(entities).hasSize(2);
        assertThat(entities.getFirst().getRunId()).isEqualTo("run-2");
        assertThat(entities.getLast().getRunId()).isEqualTo("run-1");
    }

    private HarnessRunEntity completedRunEntity(String runId) {
        return completedRunEntity(runId, startedAt());
    }

    private HarnessRunEntity completedRunEntity(
            String runId,
            LocalDateTime startedAt
    ) {
        return HarnessRunEntity.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt(startedAt),
                null,
                null,
                null,
                null
        );
    }

    private LocalDateTime startedAt() {
        return LocalDateTime.of(2026, 1, 1, 9, 0);
    }

    private LocalDateTime finishedAt() {
        return startedAt().plusSeconds(1);
    }

    private LocalDateTime finishedAt(LocalDateTime startedAt) {
        return startedAt.plusSeconds(1);
    }
}
