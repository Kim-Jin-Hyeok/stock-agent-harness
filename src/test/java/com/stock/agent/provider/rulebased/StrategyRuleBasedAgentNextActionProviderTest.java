package com.stock.agent.provider.rulebased;

import com.stock.agent.AgentNextAction;
import com.stock.agent.provider.AgentNextActionProvider;
import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StrategyRuleBasedAgentNextActionProviderTest {
    private final AgentNextActionProvider defaultProvider =
            mock(AgentNextActionProvider.class);
    private final AgentNextActionProvider swingV1Provider =
            mock(AgentNextActionProvider.class);
    private final StrategyRuleBasedAgentNextActionProvider provider =
            new StrategyRuleBasedAgentNextActionProvider(
                    defaultProvider,
                    swingV1Provider
            );

    @Test
    void routesSwingV1ToSwingProvider() {
        HarnessRunContext context = context(new InvestmentStrategyIdentity(
                "SWING_V1",
                1,
                InvestmentHorizon.SWING
        ));
        AgentNextAction expected = AgentNextAction.requestTool(
                HarnessToolRequest.dailyPriceHistory("005930")
        );
        when(swingV1Provider.next(context)).thenReturn(expected);

        AgentNextAction actual = provider.next(context);

        assertThat(actual).isSameAs(expected);
        verify(swingV1Provider).next(context);
        verifyNoInteractions(defaultProvider);
    }

    @Test
    void routesDayTradingStrategyToDefaultProvider() {
        HarnessRunContext context = context(new InvestmentStrategyIdentity(
                "DAY_TRADING_V1",
                1,
                InvestmentHorizon.DAY_TRADING
        ));
        AgentNextAction expected = AgentNextAction.requestTool(
                HarnessToolRequest.currentPrice("005930")
        );
        when(defaultProvider.next(context)).thenReturn(expected);

        AgentNextAction actual = provider.next(context);

        assertThat(actual).isSameAs(expected);
        verify(defaultProvider).next(context);
        verifyNoInteractions(swingV1Provider);
    }

    @Test
    void routesLongTermStrategyToDefaultProvider() {
        HarnessRunContext context = context(new InvestmentStrategyIdentity(
                "LONG_TERM_V1",
                1,
                InvestmentHorizon.LONG_TERM
        ));
        AgentNextAction expected = AgentNextAction.requestTool(
                HarnessToolRequest.dailyPriceHistory("005930")
        );
        when(defaultProvider.next(context)).thenReturn(expected);

        AgentNextAction actual = provider.next(context);

        assertThat(actual).isSameAs(expected);
        verify(defaultProvider).next(context);
        verifyNoInteractions(swingV1Provider);
    }

    @Test
    void rejectsUnsupportedSwingStrategyVersion() {
        HarnessRunContext context = context(new InvestmentStrategyIdentity(
                "SWING_V1",
                2,
                InvestmentHorizon.SWING
        ));

        assertThatIllegalStateException()
                .isThrownBy(() -> provider.next(context))
                .withMessage(
                        "Unsupported rule-based swing strategy. "
                                + "strategyId=SWING_V1, strategyVersion=2, "
                                + "horizon=SWING"
                );
        verifyNoInteractions(defaultProvider, swingV1Provider);
    }

    private HarnessRunContext context(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        return new HarnessRunContext(
                "run-1",
                strategyIdentity,
                new HarnessRunLimits(10, 5),
                HarnessAllowedTools.readOnly(),
                new PortfolioSnapshot(1_000_000L, 1_000_000L, List.of()),
                new MarketSnapshot(
                        "KOSPI",
                        true,
                        "Test market snapshot."
                ),
                List.of("005930"),
                List.of()
        );
    }
}
