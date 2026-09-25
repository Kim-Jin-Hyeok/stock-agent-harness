package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class InvestmentAgentTest {

    private final InvestmentAgent agent = new InvestmentAgent();

    @Test
    void nextRequestsDailyPriceHistoryForFirstCandidate() {
        HarnessRunContext context = runContext(
                List.of("005930", "000660"),
                List.of()
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.dailyPriceHistory("005930")
        );
        assertThat(action.investmentDecision()).isNull();
    }

    @Test
    void nextRequestsCurrentPriceAfterDailyPriceHistoryIsReceived() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(dailyPriceHistoryResult("005930", List.of(dailyBar())))
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.currentPrice("005930")
        );
        assertThat(action.investmentDecision()).isNull();
    }

    @Test
    void nextReturnsHoldAfterDailyPriceHistoryAndCurrentPriceAreReceived() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(
                        dailyPriceHistoryResult(
                                "005930",
                                List.of(dailyBar())
                        ),
                        currentPriceResult("005930", 70_000L)
                )
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.HOLD);
        assertThat(action.investmentDecision().symbol()).isNull();
        assertThat(action.investmentDecision().quantity()).isNull();
        assertThat(action.investmentDecision().expectedPriceKrw()).isNull();
        assertThat(action.investmentDecision().reason())
                .isEqualTo(
                        "Market data received. symbol=005930, "
                                + "dailyBars=1, latestTradingDate=2026-09-23, "
                                + "latestClosePriceKrw=69000, "
                                + "currentPriceKrw=70000, source=PROVIDER"
                );
    }

    @Test
    void nextReturnsHoldWithoutCurrentPriceWhenDailyPriceHistoryIsEmpty() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(dailyPriceHistoryResult("005930", List.of()))
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.HOLD);
        assertThat(action.investmentDecision().reason()).isEqualTo(
                "Daily price history is empty. symbol=005930"
        );
    }

    @Test
    void nextRequestsCandidateHistoryWhenAnotherSymbolResultExists() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(dailyPriceHistoryResult(
                        "000660",
                        List.of(dailyBar())
                ))
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.dailyPriceHistory("005930")
        );
    }

    @Test
    void nextRequestsCandidatePriceWhenAnotherSymbolResultExists() {
        HarnessRunContext context = runContext(
                List.of("005930"),
                List.of(
                        dailyPriceHistoryResult(
                                "005930",
                                List.of(dailyBar())
                        ),
                        currentPriceResult("000660", 120_000L)
                )
        );

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.currentPrice("005930")
        );
    }

    @Test
    void nextRejectsEmptyCandidateSymbols() {
        HarnessRunContext context = runContext(List.of(), List.of());

        assertThatIllegalStateException()
                .isThrownBy(() -> agent.next(context))
                .withMessage(
                        "Investment agent requires at least one candidate symbol."
                );
    }

    private HarnessRunContext runContext(
            List<String> candidateSymbols,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return new HarnessRunContext(
                "run-1",
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                new HarnessRunLimits(10, 5),
                HarnessAllowedTools.readOnly(),
                portfolioSnapshot(),
                marketSnapshot(),
                candidateSymbols,
                toolResults
        );
    }

    private HarnessToolExecutionResult dailyPriceHistoryResult(
            String symbol,
            List<DailyPriceBar> bars
    ) {
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.dailyPriceHistory(symbol),
                HarnessToolOutput.dailyPriceHistory(
                        new DailyPriceHistory(symbol, bars)
                )
        );
    }

    private HarnessToolExecutionResult currentPriceResult(
            String symbol,
            long priceKrw
    ) {
        CurrentPriceSnapshot snapshot = new CurrentPriceSnapshot(
                symbol,
                priceKrw,
                Instant.parse("2026-09-24T00:00:00Z")
        );
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.currentPrice(symbol),
                HarnessToolOutput.currentPrice(
                        CurrentPriceLookupResult.provider(snapshot)
                )
        );
    }

    private DailyPriceBar dailyBar() {
        return new DailyPriceBar(
                LocalDate.of(2026, 9, 23),
                67_000L,
                71_000L,
                66_000L,
                69_000L,
                1_000_000L
        );
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_000_000L,
                List.of()
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KOSPI",
                true,
                "Test market snapshot."
        );
    }
}
