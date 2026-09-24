package com.stock.harness;

import com.stock.harness.persistence.HarnessDecisionSnapshot;
import com.stock.harness.persistence.HarnessMarketSnapshot;
import com.stock.harness.persistence.HarnessPortfolioSnapshot;
import com.stock.harness.persistence.HarnessRiskCheckSnapshot;
import com.stock.harness.persistence.HarnessToolExecutionSnapshot;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeRecord;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunDetail(
        String runId,
        InvestmentStrategyIdentity strategyIdentity,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<String> candidateSymbols,
        HarnessDecisionSnapshot decisionSnapshot,
        HarnessRiskCheckSnapshot riskCheckSnapshot,
        HarnessPortfolioSnapshot portfolioSnapshot,
        HarnessMarketSnapshot marketSnapshot,
        List<HarnessToolExecutionSnapshot> toolExecutionSnapshots,
        List<HarnessStepResult> steps,
        List<TradeRecord> tradeRecords
) {
    public HarnessRunDetail {
        candidateSymbols = List.copyOf(candidateSymbols);
    }
}
