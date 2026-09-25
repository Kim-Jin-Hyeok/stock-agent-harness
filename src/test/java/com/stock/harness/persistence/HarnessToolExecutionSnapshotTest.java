package com.stock.harness.persistence;

import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolExecutionSnapshotTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void fromConvertsPortfolioToolExecutionResult() {
        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.portfolio(portfolioSnapshot())
                )
        );

        assertThat(snapshot.status()).isEqualTo(HarnessToolExecutionStatus.EXECUTED);
        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_PORTFOLIO);
        assertThat(snapshot.reasonCode()).isEqualTo(HarnessToolExecutionReasonCode.TOOL_EXECUTED);
        assertThat(snapshot.reason()).isEqualTo("Harness tool execution completed.");
        assertThat(snapshot.portfolioSnapshot()).isEqualTo(
                HarnessPortfolioSnapshot.from(portfolioSnapshot())
        );
        assertThat(snapshot.marketSnapshot()).isNull();
    }

    @Test
    void fromConvertsMarketToolExecutionResult() {
        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executed(
                        HarnessToolOutput.market(marketSnapshot())
                )
        );

        assertThat(snapshot.status()).isEqualTo(HarnessToolExecutionStatus.EXECUTED);
        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_MARKET);
        assertThat(snapshot.marketSnapshot()).isEqualTo(
                HarnessMarketSnapshot.from(marketSnapshot())
        );
        assertThat(snapshot.portfolioSnapshot()).isNull();
    }

    @Test
    void fromConvertsCurrentPriceToolExecutionResult() {
        CurrentPriceSnapshot currentPrice = new CurrentPriceSnapshot(
                "005930",
                70_000L,
                OBSERVED_AT
        );

        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executed(
                        HarnessToolRequest.currentPrice("005930"),
                        HarnessToolOutput.currentPrice(
                                CurrentPriceLookupResult.cache(currentPrice)
                        )
                )
        );

        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(snapshot.request()).isEqualTo(
                new HarnessToolRequestSnapshot(HarnessToolType.GET_CURRENT_PRICE, "005930")
        );
        assertThat(snapshot.currentPriceSnapshot()).isEqualTo(
                HarnessCurrentPriceSnapshot.from(currentPrice)
        );
        assertThat(snapshot.currentPriceSource()).isEqualTo(CurrentPriceLookupSource.CACHE);
        assertThat(snapshot.portfolioSnapshot()).isNull();
        assertThat(snapshot.marketSnapshot()).isNull();
    }

    @Test
    void fromConvertsDailyPriceHistoryToolExecutionResult() {
        DailyPriceHistory history = dailyPriceHistory();

        HarnessToolExecutionSnapshot snapshot =
                HarnessToolExecutionSnapshot.from(
                        HarnessToolExecutionResult.executed(
                                HarnessToolRequest.dailyPriceHistory("005930"),
                                HarnessToolOutput.dailyPriceHistory(history)
                        )
                );

        assertThat(snapshot.type())
                .isEqualTo(HarnessToolType.GET_DAILY_PRICE_HISTORY);
        assertThat(snapshot.request()).isEqualTo(
                new HarnessToolRequestSnapshot(
                        HarnessToolType.GET_DAILY_PRICE_HISTORY,
                        "005930"
                )
        );
        assertThat(snapshot.dailyPriceHistorySnapshot()).isEqualTo(
                HarnessDailyPriceHistorySnapshot.from(history)
        );
        assertThat(snapshot.currentPriceSnapshot()).isNull();
        assertThat(snapshot.currentPriceSource()).isNull();
    }

    @Test
    void fromHandlesExecutionResultWithoutOutput() {
        HarnessToolExecutionSnapshot snapshot = HarnessToolExecutionSnapshot.from(
                HarnessToolExecutionResult.executionFailed(
                        HarnessToolRequest.currentPrice("005930"),
                        "Broker timeout"
                )
        );

        assertThat(snapshot.status()).isEqualTo(HarnessToolExecutionStatus.FAILED);
        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(snapshot.reasonCode()).isEqualTo(HarnessToolExecutionReasonCode.TOOL_EXECUTION_FAILED);
        assertThat(snapshot.request()).isEqualTo(
                new HarnessToolRequestSnapshot(HarnessToolType.GET_CURRENT_PRICE, "005930")
        );
        assertThat(snapshot.currentPriceSource()).isNull();
        assertThat(snapshot.portfolioSnapshot()).isNull();
        assertThat(snapshot.marketSnapshot()).isNull();
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                5_000_000L,
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

    private DailyPriceHistory dailyPriceHistory() {
        return new DailyPriceHistory(
                "005930",
                List.of(new DailyPriceBar(
                        LocalDate.of(2026, 1, 2),
                        69_000L,
                        71_000L,
                        68_000L,
                        70_000L,
                        1_000_000L
                ))
        );
    }
}
