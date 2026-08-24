package com.stock.harness;

import com.stock.harness.persistence.HarnessDecisionSnapshot;
import com.stock.harness.persistence.HarnessMarketSnapshot;
import com.stock.harness.persistence.HarnessPortfolioSnapshot;
import com.stock.harness.persistence.HarnessRiskCheckSnapshot;
import com.stock.trade.TradeRecord;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunDetail(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        HarnessDecisionSnapshot decisionSnapshot,
        HarnessRiskCheckSnapshot riskCheckSnapshot,
        HarnessPortfolioSnapshot portfolioSnapshot,
        HarnessMarketSnapshot marketSnapshot,
        List<HarnessStepResult> steps,
        List<TradeRecord> tradeRecords
) {
}
