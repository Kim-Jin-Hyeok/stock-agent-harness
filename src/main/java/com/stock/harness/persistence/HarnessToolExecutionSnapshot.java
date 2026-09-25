package com.stock.harness.persistence;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.price.lookup.CurrentPriceLookupSource;

public record HarnessToolExecutionSnapshot(
        HarnessToolExecutionStatus status,
        HarnessToolType type,
        HarnessToolExecutionReasonCode reasonCode,
        String reason,
        HarnessPortfolioSnapshot portfolioSnapshot,
        HarnessMarketSnapshot marketSnapshot,
        HarnessCurrentPriceSnapshot currentPriceSnapshot,
        HarnessToolRequestSnapshot request,
        CurrentPriceLookupSource currentPriceSource,
        HarnessDailyPriceHistorySnapshot dailyPriceHistorySnapshot
) {
    public HarnessToolExecutionSnapshot(
            HarnessToolExecutionStatus status,
            HarnessToolType type,
            HarnessToolExecutionReasonCode reasonCode,
            String reason,
            HarnessPortfolioSnapshot portfolioSnapshot,
            HarnessMarketSnapshot marketSnapshot
    ) {
        this(
                status,
                type,
                reasonCode,
                reason,
                portfolioSnapshot,
                marketSnapshot,
                null,
                null,
                null,
                null
        );
    }

    public HarnessToolExecutionSnapshot(
            HarnessToolExecutionStatus status,
            HarnessToolType type,
            HarnessToolExecutionReasonCode reasonCode,
            String reason,
            HarnessPortfolioSnapshot portfolioSnapshot,
            HarnessMarketSnapshot marketSnapshot,
            HarnessCurrentPriceSnapshot currentPriceSnapshot
    ) {
        this(
                status,
                type,
                reasonCode,
                reason,
                portfolioSnapshot,
                marketSnapshot,
                currentPriceSnapshot,
                null,
                null,
                null
        );
    }

    public HarnessToolExecutionSnapshot(
            HarnessToolExecutionStatus status,
            HarnessToolType type,
            HarnessToolExecutionReasonCode reasonCode,
            String reason,
            HarnessPortfolioSnapshot portfolioSnapshot,
            HarnessMarketSnapshot marketSnapshot,
            HarnessCurrentPriceSnapshot currentPriceSnapshot,
            HarnessToolRequestSnapshot request
    ) {
        this(
                status,
                type,
                reasonCode,
                reason,
                portfolioSnapshot,
                marketSnapshot,
                currentPriceSnapshot,
                request,
                null,
                null
        );
    }

    public HarnessToolExecutionSnapshot(
            HarnessToolExecutionStatus status,
            HarnessToolType type,
            HarnessToolExecutionReasonCode reasonCode,
            String reason,
            HarnessPortfolioSnapshot portfolioSnapshot,
            HarnessMarketSnapshot marketSnapshot,
            HarnessCurrentPriceSnapshot currentPriceSnapshot,
            HarnessToolRequestSnapshot request,
            CurrentPriceLookupSource currentPriceSource
    ) {
        this(
                status,
                type,
                reasonCode,
                reason,
                portfolioSnapshot,
                marketSnapshot,
                currentPriceSnapshot,
                request,
                currentPriceSource,
                null
        );
    }

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

        HarnessCurrentPriceSnapshot currentPriceSnapshot = output == null
                || output.currentPriceSnapshot() == null
                ? null
                : HarnessCurrentPriceSnapshot.from(output.currentPriceSnapshot());

        HarnessDailyPriceHistorySnapshot dailyPriceHistorySnapshot =
                output == null || output.dailyPriceHistory() == null
                        ? null
                        : HarnessDailyPriceHistorySnapshot.from(
                                output.dailyPriceHistory()
                        );

        return new HarnessToolExecutionSnapshot(
                result.status(),
                result.type(),
                result.reasonCode(),
                result.reason(),
                portfolioSnapshot,
                marketSnapshot,
                currentPriceSnapshot,
                result.request() == null
                        ? null
                        : HarnessToolRequestSnapshot.from(result.request()),
                output == null ? null : output.currentPriceSource(),
                dailyPriceHistorySnapshot
        );
    }
}
