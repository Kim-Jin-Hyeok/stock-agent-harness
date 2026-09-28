package com.stock.agent.provider.ai.request;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentNextActionAiRequestFactoryTest {
    private static final String SYMBOL = "005930";

    private final AgentNextActionAiRequestFactory factory =
            new AgentNextActionAiRequestFactory();

    @Test
    void createsAiRequestFromHarnessRunContext() {
        PortfolioSnapshot portfolioSnapshot = portfolioSnapshot();
        MarketSnapshot marketSnapshot = marketSnapshot();
        HarnessToolExecutionResult toolResult = currentPriceResult();
        HarnessRunContext context = context(
                portfolioSnapshot,
                marketSnapshot,
                List.of(toolResult)
        );

        AgentNextActionAiRequest request = factory.create(context);

        assertThat(request.strategyId()).isEqualTo("DAY_TRADING_V1");
        assertThat(request.strategyVersion()).isEqualTo(1);
        assertThat(request.horizon())
                .isEqualTo(InvestmentHorizon.DAY_TRADING);
        assertThat(request.allowedToolTypes())
                .containsExactly(
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolType.GET_MARKET,
                        HarnessToolType.GET_CURRENT_PRICE,
                        HarnessToolType.GET_DAILY_PRICE_HISTORY
                );
        assertThat(request.candidateSymbols()).containsExactly(SYMBOL);
        assertThat(request.portfolioSnapshot()).isSameAs(portfolioSnapshot);
        assertThat(request.marketSnapshot()).isSameAs(marketSnapshot);
        assertThat(request.toolResults()).containsExactly(toolResult);
    }

    @Test
    void exposesImmutableLists() {
        AgentNextActionAiRequest request = factory.create(context(
                portfolioSnapshot(),
                marketSnapshot(),
                List.of(currentPriceResult())
        ));

        assertThatThrownBy(() -> request.allowedToolTypes().add(
                HarnessToolType.GET_CURRENT_PRICE
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.candidateSymbols().add("000660"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.toolResults().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> factory.create(null))
                .withMessage("context must not be null.");
    }

    private HarnessRunContext context(
            PortfolioSnapshot portfolioSnapshot,
            MarketSnapshot marketSnapshot,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return new HarnessRunContext(
                "run-1",
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                new HarnessRunLimits(10, 5, 1, 5),
                HarnessAllowedTools.readOnly(),
                portfolioSnapshot,
                marketSnapshot,
                List.of(SYMBOL),
                toolResults
        );
    }

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                9_000_000L,
                10_000_000L,
                List.of(new PortfolioPosition(
                        SYMBOL,
                        10L,
                        100_000L,
                        1_000_000L
                ))
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private HarnessToolExecutionResult currentPriceResult() {
        return HarnessToolExecutionResult.executed(
                HarnessToolOutput.currentPrice(new CurrentPriceSnapshot(
                        SYMBOL,
                        101_000L,
                        Instant.parse("2026-09-28T01:00:00Z")
                ))
        );
    }
}
