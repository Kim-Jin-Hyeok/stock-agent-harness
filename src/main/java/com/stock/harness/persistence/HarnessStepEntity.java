package com.stock.harness.persistence;

import com.stock.harness.HarnessStepResult;
import com.stock.harness.HarnessStepStatus;
import com.stock.harness.HarnessStepType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HarnessStepEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String runId;

    private Integer stepOrder;

    @Enumerated(EnumType.STRING)
    private HarnessStepType type;

    @Enumerated(EnumType.STRING)
    private HarnessStepStatus status;

    private String message;

    public static HarnessStepEntity of(
            String runId,
            Integer stepOrder,
            HarnessStepType type,
            HarnessStepStatus status,
            String message
    ) {
        HarnessStepEntity entity = new HarnessStepEntity();
        entity.runId = runId;
        entity.stepOrder = stepOrder;
        entity.type = type;
        entity.status = status;
        entity.message = message;

        return entity;
    }

    public static HarnessStepEntity from(
            String runId,
            Integer stepOrder,
            HarnessStepResult result) {
        return of(
                runId,
                stepOrder,
                result.type(),
                result.status(),
                result.message()
        );
    }
}
