package com.stock.harness.persistence;

import com.stock.harness.HarnessStepStatus;
import com.stock.harness.HarnessStepType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HarnessStepRepositoryTest {

    @Autowired
    private HarnessStepRepository harnessStepRepository;

    @Test
    void findAllByRunIdOrderByStepOrderAscReturnsMatchingStepsInOrder() {
        // given
        harnessStepRepository.save(completedStepEntity("run-1", 2));
        harnessStepRepository.save(completedStepEntity("run-2", 1));
        harnessStepRepository.save(authorizedToolRequestStepEntity("run-1", 1));

        // when
        List<HarnessStepEntity> entities = harnessStepRepository.findAllByRunIdOrderByStepOrderAsc("run-1");

        // then
        assertThat(entities).hasSize(2);
        assertThat(entities)
                .extracting(HarnessStepEntity::getRunId)
                .containsExactly("run-1", "run-1");
        assertThat(entities)
                .extracting(HarnessStepEntity::getStepOrder)
                .containsExactly(1, 2);
        assertThat(entities)
                .extracting(HarnessStepEntity::getType)
                .containsExactly(
                        HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                        HarnessStepType.CHECK_STEP_LIMIT
                );
    }

    private HarnessStepEntity completedStepEntity(String runId, Integer stepOrder) {
        return stepEntity(
                runId,
                stepOrder,
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepStatus.COMPLETED,
                "Test completed step."
        );
    }

    private HarnessStepEntity failedStepEntity(String runId, Integer stepOrder) {
        return stepEntity(
                runId,
                stepOrder,
                HarnessStepType.RUN_FAILED,
                HarnessStepStatus.FAILED,
                "Test failed step."
        );
    }

    private HarnessStepEntity authorizedToolRequestStepEntity(String runId, Integer stepOrder) {
        return stepEntity(
                runId,
                stepOrder,
                HarnessStepType.AUTHORIZE_TOOL_REQUEST,
                HarnessStepStatus.COMPLETED,
                "Tool authorization completed."
        );
    }

    private HarnessStepEntity stepEntity(
            String runId,
            Integer stepOrder,
            HarnessStepType type,
            HarnessStepStatus status,
            String message
    ) {
        LocalDateTime recordedAt = LocalDateTime.now();

        return HarnessStepEntity.of(
                runId,
                stepOrder,
                type,
                status,
                message,
                recordedAt,
                recordedAt
        );
    }
}
