package com.stock.agent.provider.rulebased.swing.v1;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.decision.swing.v1.SwingV1DecisionInput;
import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SwingV1AgentNextActionProviderTest {
    private static final String CANDIDATE_SYMBOL = "005930";
    private static final String POSITION_SYMBOL = "000660";
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-29T06:00:00Z");
    private static final Instant EVALUATED_AT =
            Instant.parse("2026-09-29T06:01:00Z");

    private final SwingV1DecisionService decisionService =
            mock(SwingV1DecisionService.class);
    private final SwingV1AgentNextActionProvider provider =
            new SwingV1AgentNextActionProvider(
                    decisionService,
                    Clock.fixed(EVALUATED_AT, ZoneOffset.UTC)
            );

    @Test
    void requestsDailyPriceHistoryBeforeCurrentPrices() {
        HarnessRunContext context = runContext(
                emptyPortfolio(),
                List.of()
        );

        AgentNextAction action = provider.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.dailyPriceHistory(CANDIDATE_SYMBOL)
        );
        verifyNoInteractions(decisionService);
    }

    @Test
    void requestsCandidateCurrentPriceAfterHistory() {
        HarnessRunContext context = runContext(
                emptyPortfolio(),
                List.of(dailyPriceHistoryResult())
        );

        AgentNextAction action = provider.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.currentPrice(CANDIDATE_SYMBOL)
        );
        verifyNoInteractions(decisionService);
    }

    @Test
    void requestsMissingPositionPriceWithoutRepeatingCandidatePrice() {
        PortfolioSnapshot portfolio = portfolioWithCandidateAndPosition();
        HarnessRunContext context = runContext(
                portfolio,
                List.of(
                        dailyPriceHistoryResult(),
                        currentPriceResult(
                                CANDIDATE_SYMBOL,
                                70_000L,
                                CurrentPriceLookupSource.PROVIDER
                        )
                )
        );

        AgentNextAction action = provider.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(
                HarnessToolRequest.currentPrice(POSITION_SYMBOL)
        );
        verifyNoInteractions(decisionService);
    }

    @Test
    void delegatesCollectedToolResultsToDecisionService() {
        PortfolioSnapshot portfolio = portfolioWithOtherPosition();
        DailyPriceHistory history = dailyPriceHistory();
        CurrentPriceSnapshot candidatePrice = currentPrice(
                CANDIDATE_SYMBOL,
                70_000L
        );
        CurrentPriceSnapshot positionPrice = currentPrice(
                POSITION_SYMBOL,
                130_000L
        );
        SwingV1DecisionInput input = new SwingV1DecisionInput(
                STRATEGY_IDENTITY,
                history,
                portfolio,
                candidatePrice,
                CurrentPriceLookupSource.PROVIDER,
                List.of(candidatePrice, positionPrice),
                EVALUATED_AT
        );
        InvestmentDecision decision = new InvestmentDecision(
                InvestmentAction.BUY,
                CANDIDATE_SYMBOL,
                2L,
                70_000L,
                "Test SWING_V1 decision."
        );
        when(decisionService.decide(input)).thenReturn(decision);

        AgentNextAction action = provider.next(runContext(
                portfolio,
                List.of(
                        dailyPriceHistoryResult(history),
                        currentPriceResult(
                                candidatePrice,
                                CurrentPriceLookupSource.PROVIDER
                        ),
                        currentPriceResult(
                                positionPrice,
                                CurrentPriceLookupSource.CACHE
                        )
                )
        ));

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(action.investmentDecision().symbol())
                .isEqualTo(CANDIDATE_SYMBOL);
        assertThat(action.investmentDecision().quantity()).isEqualTo(2L);
        assertThat(action.investmentDecision().expectedPriceKrw())
                .isEqualTo(70_000L);
        verify(decisionService).decide(input);
    }

    @Test
    void rejectsEmptyCandidateSymbols() {
        HarnessRunContext context = new HarnessRunContext(
                "run-1",
                STRATEGY_IDENTITY,
                new HarnessRunLimits(10, 5),
                HarnessAllowedTools.readOnly(),
                emptyPortfolio(),
                marketSnapshot(),
                List.of(),
                List.of()
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.next(context))
                .withMessage(
                        "SWING_V1 agent requires at least one candidate "
                                + "symbol."
                );
    }

    private HarnessRunContext runContext(
            PortfolioSnapshot portfolio,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return new HarnessRunContext(
                "run-1",
                STRATEGY_IDENTITY,
                new HarnessRunLimits(10, 5),
                HarnessAllowedTools.readOnly(),
                portfolio,
                marketSnapshot(),
                List.of(CANDIDATE_SYMBOL),
                toolResults
        );
    }

    private HarnessToolExecutionResult dailyPriceHistoryResult() {
        return dailyPriceHistoryResult(dailyPriceHistory());
    }

    private HarnessToolExecutionResult dailyPriceHistoryResult(
            DailyPriceHistory history
    ) {
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.dailyPriceHistory(CANDIDATE_SYMBOL),
                HarnessToolOutput.dailyPriceHistory(history)
        );
    }

    private HarnessToolExecutionResult currentPriceResult(
            String symbol,
            long priceKrw,
            CurrentPriceLookupSource source
    ) {
        return currentPriceResult(
                currentPrice(symbol, priceKrw),
                source
        );
    }

    private HarnessToolExecutionResult currentPriceResult(
            CurrentPriceSnapshot currentPrice,
            CurrentPriceLookupSource source
    ) {
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.currentPrice(currentPrice.symbol()),
                HarnessToolOutput.currentPrice(
                        new CurrentPriceLookupResult(currentPrice, source)
                )
        );
    }

    private DailyPriceHistory dailyPriceHistory() {
        return new DailyPriceHistory(CANDIDATE_SYMBOL, List.of());
    }

    private CurrentPriceSnapshot currentPrice(
            String symbol,
            long priceKrw
    ) {
        return new CurrentPriceSnapshot(
                symbol,
                priceKrw,
                OBSERVED_AT
        );
    }

    private PortfolioSnapshot emptyPortfolio() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_000_000L,
                List.of()
        );
    }

    private PortfolioSnapshot portfolioWithCandidateAndPosition() {
        return new PortfolioSnapshot(
                1_000_000L,
                2_300_000L,
                List.of(
                        new PortfolioPosition(
                                CANDIDATE_SYMBOL,
                                10L,
                                70_000L,
                                700_000L
                        ),
                        new PortfolioPosition(
                                POSITION_SYMBOL,
                                5L,
                                120_000L,
                                600_000L
                        )
                )
        );
    }

    private PortfolioSnapshot portfolioWithOtherPosition() {
        return new PortfolioSnapshot(
                1_000_000L,
                1_600_000L,
                List.of(new PortfolioPosition(
                        POSITION_SYMBOL,
                        5L,
                        120_000L,
                        600_000L
                ))
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
