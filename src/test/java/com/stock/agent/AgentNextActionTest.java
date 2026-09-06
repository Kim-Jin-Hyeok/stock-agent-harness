package com.stock.agent;

import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentNextActionTest {

    @Test
    void createsToolRequestAction() {
        HarnessToolRequest request = portfolioToolRequest();

        AgentNextAction action = AgentNextAction.requestTool(request);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest()).isEqualTo(request);
        assertThat(action.investmentDecision()).isNull();
    }

    @Test
    void createsFinalDecisionAction() {
        InvestmentDecision decision = holdDecision();

        AgentNextAction action = AgentNextAction.finalDecision(decision);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision()).isEqualTo(decision);
    }

    @Test
    void createsFinalBuyDecisionAction() {
        InvestmentDecision decision = buyDecision();

        AgentNextAction action = AgentNextAction.finalDecision(decision);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision().action()).isEqualTo(InvestmentAction.BUY);
        assertThat(action.investmentDecision().symbol()).isEqualTo("005930");
        assertThat(action.investmentDecision().quantity()).isEqualTo(1L);
        assertThat(action.investmentDecision().expectedPriceKrw()).isEqualTo(70_000L);
    }

    private HarnessToolRequest portfolioToolRequest() {
        return new HarnessToolRequest(portfolioTool());
    }

    private HarnessToolRequest marketToolRequest() {
        return new HarnessToolRequest(marketTool());
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Test hold decision."
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                1L,
                70_000L,
                "Test buy decision."
        );
    }

    private HarnessToolType portfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType marketTool() {
        return HarnessToolType.GET_MARKET;
    }
}
