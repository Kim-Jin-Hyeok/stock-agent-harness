package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import com.stock.harness.HarnessRunLimits;
import com.stock.harness.tool.HarnessAllowedTools;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentAgentTest {

    @Test
    void nextReturnsFinalDecisionAction() {
        InvestmentAgent agent = investmentAgent();
        HarnessRunContext context = runContext();

        AgentNextAction action = agent.next(context);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision()).isNotNull();
    }

    @Test
    void nextWrapsCurrentHoldDecision() {
        InvestmentAgent agent = investmentAgent();
        HarnessRunContext context = runContext();

        AgentNextAction action = agent.next(context);

        assertThat(action.investmentDecision().action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(action.investmentDecision().symbol()).isNull();
        assertThat(action.investmentDecision().quantity()).isNull();
        assertThat(action.investmentDecision().expectedPriceKrw()).isNull();
    }

    @Test
    void nextDecisionReasonIncludesContextValues() {
        InvestmentAgent agent = investmentAgent();
        HarnessRunContext context = runContext();

        AgentNextAction action = agent.next(context);

        assertThat(action.investmentDecision().reason())
                .contains("cashAmountKrw=1000000")
                .contains("marketOpen=true")
                .contains("allowedTools=[GET_PORTFOLIO, GET_MARKET]");
    }

    private InvestmentAgent investmentAgent() {
        return new InvestmentAgent();
    }

    private HarnessRunContext runContext() {
        return new HarnessRunContext(
                "run-1",
                runLimits(),
                allowedTools(),
                portfolioSnapshot(),
                marketSnapshot(),
                List.of()
        );
    }

    private HarnessRunLimits runLimits() {
        return new HarnessRunLimits(10);
    }

    private HarnessAllowedTools allowedTools() {
        return HarnessAllowedTools.readOnly();
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
