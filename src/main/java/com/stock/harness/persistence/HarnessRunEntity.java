package com.stock.harness.persistence;

import com.stock.harness.*;
import com.stock.trade.TradeRecord;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HarnessRunEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String runId;

    @Enumerated(EnumType.STRING)
    private HarnessRunStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    public static HarnessRunEntity of(
            String runId,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        HarnessRunEntity entity = new HarnessRunEntity();
        entity.runId = runId;
        entity.status = status;
        entity.startedAt = startedAt;
        entity.finishedAt = finishedAt;
        return entity;
    }

    public static HarnessRunEntity from(HarnessRunResult result) {
        return of(
                result.runId(),
                result.status(),
                result.startedAt(),
                result.finishedAt()
        );
    }

    public HarnessRunSummary toSummary() {
        return new HarnessRunSummary(
                runId,
                status,
                startedAt,
                finishedAt
        );
    }

    public HarnessRunDetail toDetail(
            List<HarnessStepResult> steps,
            List<TradeRecord> tradeRecords
    ) {
        return new HarnessRunDetail(
                runId,
                status,
                startedAt,
                finishedAt,
                steps,
                tradeRecords
        );
    }
}
