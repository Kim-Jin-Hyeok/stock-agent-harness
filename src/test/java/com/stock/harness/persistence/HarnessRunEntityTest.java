package com.stock.harness.persistence;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.*;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessRunEntityTest {
    @Test
    void fromCreatesEntityWithRunMetadata() {
        // given
        String runId = "run-1";
        HarnessRunResult result = completedRunResult(runId);

        // when
        HarnessRunEntity entity = HarnessRunEntity.from(
                result,
                decisionSnapshotJson(),
                riskCheckSnapshotJson(),
                portfolioSnapshotJson()
        );

        // then
        assertThat(entity.getRunId()).isEqualTo(result.runId());
        assertThat(entity.getStatus()).isEqualTo(result.status());
        assertThat(entity.getStartedAt()).isEqualTo(result.startedAt());
        assertThat(entity.getFinishedAt()).isEqualTo(result.finishedAt());
    }

    @Test
    void fromCreatesEntityWithSnapshotJson() {
        // given
        HarnessRunResult result = completedRunResult("run-1");

        // when
        HarnessRunEntity entity = HarnessRunEntity.from(
                result,
                decisionSnapshotJson(),
                riskCheckSnapshotJson(),
                portfolioSnapshotJson()
        );

        // then
        assertThat(entity.getDecisionSnapshotJson()).isEqualTo(decisionSnapshotJson());
        assertThat(entity.getRiskCheckSnapshotJson()).isEqualTo(riskCheckSnapshotJson());
        assertThat(entity.getPortfolioSnapshotJson()).isEqualTo(portfolioSnapshotJson());
    }

    @Test
    void toSummaryCreatesRunSummaryWithRunMetadata() {
        // given
        String runId = "run-1";
        HarnessRunResult result = completedRunResult(runId);
        HarnessRunEntity entity = HarnessRunEntity.from(
                result,
                decisionSnapshotJson(),
                riskCheckSnapshotJson(),
                portfolioSnapshotJson()
        );

        // when
        HarnessRunSummary summary = entity.toSummary();

        // then
        assertThat(summary.runId()).isEqualTo(entity.getRunId());
        assertThat(summary.status()).isEqualTo(entity.getStatus());
        assertThat(summary.startedAt()).isEqualTo(entity.getStartedAt());
        assertThat(summary.finishedAt()).isEqualTo(entity.getFinishedAt());
    }

    private HarnessRunResult completedRunResult(String runId) {
        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt(),
                finishedAt(),
                List.of(completedStep()),
                buyDecision(),
                approvedRiskCheckResult(),
                executedBuyTradeResult(),
                portfolioSnapshot(),
                marketSnapshot()
        );
    }

    private HarnessStepResult completedStep() {
        return new HarnessStepResult(
                HarnessStepType.EXECUTE_TRADE,
                HarnessStepStatus.COMPLETED,
                "Trade execution completed."
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                "Buy Samsung Electronics."
        );
    }

    private RiskCheckResult approvedRiskCheckResult() {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                RiskReasonCode.RISK_APPROVED,
                "Risk check approved."
        );
    }

    private TradeResult executedBuyTradeResult() {
        return new TradeResult(
                TradeStatus.EXECUTED,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution completed."
        );
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                9_300_000L,
                10_000_000L,
                List.of()
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private LocalDateTime startedAt() {
        return LocalDateTime.of(2026, 1, 1, 9, 0);
    }

    private LocalDateTime finishedAt() {
        return startedAt().plusSeconds(1);
    }

    private String decisionSnapshotJson() {
        return "{\"action\":\"BUY\"}";
    }

    private String riskCheckSnapshotJson() {
        return "{\"status\":\"APPROVED\"}";
    }

    private String portfolioSnapshotJson() {
        return "{\"cashAmountKrw\":9300000,\"totalAssetAmountKrw\":10000000}";
    }
}
