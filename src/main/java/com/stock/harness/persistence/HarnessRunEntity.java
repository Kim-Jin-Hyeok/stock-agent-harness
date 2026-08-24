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

    private String decisionSnapshotJson;

    private String riskCheckSnapshotJson;

    private String portfolioSnapshotJson;

    private String marketSnapshotJson;

    public static HarnessRunEntity of(
            String runId,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String decisionSnapshotJson,
            String riskCheckSnapshotJson,
            String portfolioSnapshotJson,
            String marketSnapshotJson
    ) {
        HarnessRunEntity entity = new HarnessRunEntity();
        entity.runId = runId;
        entity.status = status;
        entity.startedAt = startedAt;
        entity.finishedAt = finishedAt;
        entity.decisionSnapshotJson = decisionSnapshotJson;
        entity.riskCheckSnapshotJson = riskCheckSnapshotJson;
        entity.portfolioSnapshotJson = portfolioSnapshotJson;
        entity.marketSnapshotJson = marketSnapshotJson;
        return entity;
    }

    public static HarnessRunEntity from(
            HarnessRunResult result,
            String decisionSnapshotJson,
            String riskCheckSnapshotJson,
            String portfolioSnapshotJson,
            String marketSnapshotJson
    ) {
        return of(
                result.runId(),
                result.status(),
                result.startedAt(),
                result.finishedAt(),
                decisionSnapshotJson,
                riskCheckSnapshotJson,
                portfolioSnapshotJson,
                marketSnapshotJson
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
            HarnessDecisionSnapshot decisionSnapshot,
            HarnessRiskCheckSnapshot riskCheckSnapshot,
            HarnessPortfolioSnapshot portfolioSnapshot,
            HarnessMarketSnapshot marketSnapshot,
            List<HarnessStepResult> steps,
            List<TradeRecord> tradeRecords
    ) {
        return new HarnessRunDetail(
                runId,
                status,
                startedAt,
                finishedAt,
                decisionSnapshot,
                riskCheckSnapshot,
                portfolioSnapshot,
                marketSnapshot,
                steps,
                tradeRecords
        );
    }
}
