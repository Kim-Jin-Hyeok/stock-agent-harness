package com.stock.harness.persistence;

import com.stock.harness.HarnessRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
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

    private HarnessRunEntity completedRunEntity(String runId) {
        return HarnessRunEntity.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt()
        );
    }

    private HarnessRunEntity failedRunEntity(String runId) {
        return HarnessRunEntity.of(
                runId,
                HarnessRunStatus.FAILED,
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
}
