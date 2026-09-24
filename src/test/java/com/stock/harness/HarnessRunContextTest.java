package com.stock.harness;

import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessRunContextTest {

    @Test
    void createsContextWithInitialToolResults() {
        HarnessToolExecutionResult portfolioToolResult = portfolioToolResult();
        List<HarnessToolExecutionResult> toolResults = new ArrayList<>(List.of(portfolioToolResult));
        HarnessRunContext context = runContext(toolResults);

        toolResults.add(marketToolResult());

        assertThat(context.toolResults()).containsExactly(portfolioToolResult);
    }

    @Test
    void createsContextWithImmutableCandidateSymbols() {
        List<String> candidateSymbols = new ArrayList<>(List.of("005930"));
        HarnessRunContext context = runContext(candidateSymbols, List.of());

        candidateSymbols.add("000660");

        assertThat(context.candidateSymbols()).containsExactly("005930");
    }

    @Test
    void withToolResultKeepsOriginalContextUnchanged() {
        HarnessRunContext context = runContext(List.of());

        HarnessRunContext updatedContext = context.withToolResult(portfolioToolResult());

        assertThat(context.toolResults()).isEmpty();
        assertThat(updatedContext).isNotSameAs(context);
        assertThat(updatedContext.strategyIdentity()).isEqualTo(context.strategyIdentity());
    }

    @Test
    void withToolResultAppendsExecutionResult() {
        HarnessToolExecutionResult portfolioResult = portfolioToolResult();
        HarnessToolExecutionResult marketResult = marketToolResult();
        HarnessRunContext context = runContext(List.of(portfolioResult));

        HarnessRunContext updatedContext = context.withToolResult(marketResult);

        assertThat(updatedContext.toolResults())
                .containsExactly(portfolioResult, marketResult);
    }

    private HarnessRunContext runContext(List<HarnessToolExecutionResult> toolResults) {
        return runContext(List.of("005930"), toolResults);
    }

    private HarnessRunContext runContext(
            List<String> candidateSymbols,
            List<HarnessToolExecutionResult> toolResults
    ) {
        return new HarnessRunContext(
                "run-1",
                new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING),
                new HarnessRunLimits(10, 5),
                HarnessAllowedTools.readOnly(),
                portfolioSnapshot(),
                marketSnapshot(),
                candidateSymbols,
                toolResults
        );
    }

    private HarnessToolExecutionResult portfolioToolResult() {
        return HarnessToolExecutionResult.executed(
                HarnessToolOutput.portfolio(portfolioSnapshot())
        );
    }

    private HarnessToolExecutionResult marketToolResult() {
        return HarnessToolExecutionResult.executed(
                HarnessToolOutput.market(marketSnapshot())
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
