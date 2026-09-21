package com.stock.harness.persistence;

import com.stock.harness.HarnessRunDetail;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.HarnessRunSummary;
import com.stock.harness.HarnessStepResult;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    @Column(nullable = false)
    private String strategyId;

    @Column(nullable = false)
    private int strategyVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestmentHorizon horizon;

    @Enumerated(EnumType.STRING)
    private HarnessRunStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String decisionSnapshotJson;

    private String riskCheckSnapshotJson;

    private String portfolioSnapshotJson;

    private String marketSnapshotJson;

    @Lob
    private String toolExecutionSnapshotsJson;

    public static HarnessRunEntity of(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String decisionSnapshotJson,
            String riskCheckSnapshotJson,
            String portfolioSnapshotJson,
            String marketSnapshotJson,
            String toolExecutionSnapshotsJson
    ) {
        HarnessRunEntity entity = new HarnessRunEntity();
        entity.runId = runId;
        entity.strategyId = strategyIdentity.strategyId();
        entity.strategyVersion = strategyIdentity.strategyVersion();
        entity.horizon = strategyIdentity.horizon();
        entity.status = status;
        entity.startedAt = startedAt;
        entity.finishedAt = finishedAt;
        entity.decisionSnapshotJson = decisionSnapshotJson;
        entity.riskCheckSnapshotJson = riskCheckSnapshotJson;
        entity.portfolioSnapshotJson = portfolioSnapshotJson;
        entity.marketSnapshotJson = marketSnapshotJson;
        entity.toolExecutionSnapshotsJson = toolExecutionSnapshotsJson;
        return entity;
    }

    public static HarnessRunEntity of(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            HarnessRunStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String decisionSnapshotJson,
            String riskCheckSnapshotJson,
            String portfolioSnapshotJson,
            String marketSnapshotJson
    ) {
        return of(
                runId,
                strategyIdentity,
                status,
                startedAt,
                finishedAt,
                decisionSnapshotJson,
                riskCheckSnapshotJson,
                portfolioSnapshotJson,
                marketSnapshotJson,
                null
        );
    }

    public static HarnessRunEntity from(
            HarnessRunResult result,
            String decisionSnapshotJson,
            String riskCheckSnapshotJson,
            String portfolioSnapshotJson,
            String marketSnapshotJson,
            String toolExecutionSnapshotsJson
    ) {
        return of(
                result.runId(),
                result.strategyIdentity(),
                result.status(),
                result.startedAt(),
                result.finishedAt(),
                decisionSnapshotJson,
                riskCheckSnapshotJson,
                portfolioSnapshotJson,
                marketSnapshotJson,
                toolExecutionSnapshotsJson
        );
    }

    public static HarnessRunEntity from(
            HarnessRunResult result,
            String decisionSnapshotJson,
            String riskCheckSnapshotJson,
            String portfolioSnapshotJson,
            String marketSnapshotJson
    ) {
        return from(
                result,
                decisionSnapshotJson,
                riskCheckSnapshotJson,
                portfolioSnapshotJson,
                marketSnapshotJson,
                null
        );
    }

    public HarnessRunSummary toSummary() {
        return new HarnessRunSummary(
                runId,
                strategyIdentity(),
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
            List<HarnessToolExecutionSnapshot> toolExecutionSnapshots,
            List<HarnessStepResult> steps,
            List<TradeRecord> tradeRecords
    ) {
        return new HarnessRunDetail(
                runId,
                strategyIdentity(),
                status,
                startedAt,
                finishedAt,
                decisionSnapshot,
                riskCheckSnapshot,
                portfolioSnapshot,
                marketSnapshot,
                toolExecutionSnapshots,
                steps,
                tradeRecords
        );
    }

    private InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(
                strategyId,
                strategyVersion,
                horizon
        );
    }
}
