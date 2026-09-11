package com.stock.harness.persistence;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolType;

public record HarnessToolExecutionSnapshot(
        HarnessToolExecutionStatus status,
        HarnessToolType type,
        HarnessToolExecutionReasonCode reasonCode,
        String reason,
        HarnessPortfolioSnapshot portfolioSnapshot,
        HarnessMarketSnapshot marketSnapshot
) {
    public static HarnessToolExecutionSnapshot from(HarnessToolExecutionResult result) {
        HarnessToolOutput output = result.output();

        HarnessPortfolioSnapshot portfolioSnapshot = output == null
                || output.portfolioSnapshot() == null
                ? null
                : HarnessPortfolioSnapshot.from(output.portfolioSnapshot());

        HarnessMarketSnapshot marketSnapshot = output == null
                || output.marketSnapshot() == null
                ? null
                : HarnessMarketSnapshot.from(output.marketSnapshot());

        return new HarnessToolExecutionSnapshot(
                result.status(),
                result.type(),
                result.reasonCode(),
                result.reason(),
                portfolioSnapshot,
                marketSnapshot
        );
    }
}
