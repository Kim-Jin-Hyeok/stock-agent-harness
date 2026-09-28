package com.stock.agent.provider.ai.openai.response;

import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiAgentNextActionResponseTest {

    @Test
    void createsToolRequestResponse() {
        OpenAiAgentNextActionResponse.ToolRequest toolRequest =
                new OpenAiAgentNextActionResponse.ToolRequest(
                        HarnessToolType.GET_CURRENT_PRICE,
                        "005930"
                );

        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.REQUEST_TOOL,
                        toolRequest,
                        null
                );

        assertThat(response.type())
                .isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(response.toolRequest()).isEqualTo(toolRequest);
        assertThat(response.investmentDecision()).isNull();
    }

    @Test
    void createsFinalDecisionResponse() {
        OpenAiAgentNextActionResponse.InvestmentDecision decision =
                new OpenAiAgentNextActionResponse.InvestmentDecision(
                        InvestmentAction.BUY,
                        "005930",
                        3L,
                        70_000L,
                        "Available evidence supports the order."
                );

        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.FINAL_DECISION,
                        null,
                        decision
                );

        assertThat(response.type())
                .isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(response.toolRequest()).isNull();
        assertThat(response.investmentDecision()).isEqualTo(decision);
    }

    @Test
    void rejectsNullAgentActionType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiAgentNextActionResponse(
                        null,
                        null,
                        null
                ))
                .withMessage(
                        "OpenAI agent action type must not be null."
                );
    }

    @Test
    void rejectsNullToolRequestType() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new OpenAiAgentNextActionResponse.ToolRequest(
                                null,
                                null
                        )
                )
                .withMessage(
                        "OpenAI tool request type must not be null."
                );
    }

    @Test
    void rejectsNullInvestmentAction() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new OpenAiAgentNextActionResponse.InvestmentDecision(
                                null,
                                null,
                                null,
                                null,
                                "Wait for more evidence."
                        )
                )
                .withMessage(
                        "OpenAI investment action must not be null."
                );
    }

    @Test
    void rejectsBlankInvestmentDecisionReason() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new OpenAiAgentNextActionResponse.InvestmentDecision(
                                InvestmentAction.HOLD,
                                null,
                                null,
                                null,
                                " "
                        )
                )
                .withMessage(
                        "OpenAI investment decision reason must not be blank."
                );
    }
}
