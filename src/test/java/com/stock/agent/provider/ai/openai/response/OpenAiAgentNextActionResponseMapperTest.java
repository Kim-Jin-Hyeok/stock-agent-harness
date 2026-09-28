package com.stock.agent.provider.ai.openai.response;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiAgentNextActionResponseMapperTest {
    private final OpenAiAgentNextActionResponseMapper mapper =
            new OpenAiAgentNextActionResponseMapper();

    @Test
    void mapsCurrentPriceToolRequest() {
        OpenAiAgentNextActionResponse response = toolResponse(
                HarnessToolType.GET_CURRENT_PRICE,
                "005930"
        );

        AgentNextAction action = mapper.map(response);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest().type())
                .isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(action.toolRequest().symbol()).isEqualTo("005930");
        assertThat(action.investmentDecision()).isNull();
    }

    @Test
    void mapsDailyPriceHistoryToolRequest() {
        OpenAiAgentNextActionResponse response = toolResponse(
                HarnessToolType.GET_DAILY_PRICE_HISTORY,
                "005930"
        );

        AgentNextAction action = mapper.map(response);

        assertThat(action.type()).isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(action.toolRequest().type())
                .isEqualTo(HarnessToolType.GET_DAILY_PRICE_HISTORY);
        assertThat(action.toolRequest().symbol()).isEqualTo("005930");
    }

    @Test
    void mapsBuyFinalDecision() {
        OpenAiAgentNextActionResponse response = decisionResponse(
                InvestmentAction.BUY,
                "005930",
                3L,
                70_000L
        );

        AgentNextAction action = mapper.map(response);

        assertThat(action.type()).isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(action.toolRequest()).isNull();
        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(action.investmentDecision().symbol()).isEqualTo("005930");
        assertThat(action.investmentDecision().quantity()).isEqualTo(3L);
        assertThat(action.investmentDecision().expectedPriceKrw())
                .isEqualTo(70_000L);
        assertThat(action.investmentDecision().movingAverageEvidence())
                .isNull();
    }

    @Test
    void mapsSellFinalDecision() {
        OpenAiAgentNextActionResponse response = decisionResponse(
                InvestmentAction.SELL,
                "005930",
                2L,
                71_000L
        );

        AgentNextAction action = mapper.map(response);

        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.SELL);
        assertThat(action.investmentDecision().quantity()).isEqualTo(2L);
    }

    @Test
    void mapsHoldFinalDecision() {
        OpenAiAgentNextActionResponse response = decisionResponse(
                InvestmentAction.HOLD,
                null,
                null,
                null
        );

        AgentNextAction action = mapper.map(response);

        assertThat(action.investmentDecision().action())
                .isEqualTo(InvestmentAction.HOLD);
        assertThat(action.investmentDecision().symbol()).isNull();
        assertThat(action.investmentDecision().quantity()).isNull();
        assertThat(action.investmentDecision().expectedPriceKrw()).isNull();
    }

    @Test
    void rejectsNullResponse() {
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.map(null))
                .withMessage("response must not be null.");
    }

    @Test
    void rejectsToolRequestActionWithoutToolRequest() {
        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.REQUEST_TOOL,
                        null,
                        null
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage(
                        "OpenAI REQUEST_TOOL response must include "
                                + "toolRequest."
                );
    }

    @Test
    void rejectsToolRequestActionWithInvestmentDecision() {
        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.REQUEST_TOOL,
                        toolRequest(HarnessToolType.GET_CURRENT_PRICE),
                        decision(
                                InvestmentAction.HOLD,
                                null,
                                null,
                                null
                        )
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage(
                        "OpenAI REQUEST_TOOL response must not include "
                                + "investmentDecision."
                );
    }

    @Test
    void rejectsFinalDecisionActionWithoutInvestmentDecision() {
        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.FINAL_DECISION,
                        null,
                        null
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage(
                        "OpenAI FINAL_DECISION response must include "
                                + "investmentDecision."
                );
    }

    @Test
    void rejectsFinalDecisionActionWithToolRequest() {
        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.FINAL_DECISION,
                        toolRequest(HarnessToolType.GET_CURRENT_PRICE),
                        decision(
                                InvestmentAction.HOLD,
                                null,
                                null,
                                null
                        )
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage(
                        "OpenAI FINAL_DECISION response must not include "
                                + "toolRequest."
                );
    }

    @ParameterizedTest
    @MethodSource("holdDecisionsWithOrderFields")
    void rejectsHoldDecisionWithOrderFields(
            OpenAiAgentNextActionResponse.InvestmentDecision decision
    ) {
        OpenAiAgentNextActionResponse response =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.FINAL_DECISION,
                        null,
                        decision
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage(
                        "OpenAI HOLD decision must not include order fields."
                );
    }

    private OpenAiAgentNextActionResponse toolResponse(
            HarnessToolType type,
            String symbol
    ) {
        return new OpenAiAgentNextActionResponse(
                AgentNextActionType.REQUEST_TOOL,
                new OpenAiAgentNextActionResponse.ToolRequest(type, symbol),
                null
        );
    }

    private OpenAiAgentNextActionResponse decisionResponse(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw
    ) {
        return new OpenAiAgentNextActionResponse(
                AgentNextActionType.FINAL_DECISION,
                null,
                decision(action, symbol, quantity, expectedPriceKrw)
        );
    }

    private OpenAiAgentNextActionResponse.ToolRequest toolRequest(
            HarnessToolType type
    ) {
        return new OpenAiAgentNextActionResponse.ToolRequest(type, "005930");
    }

    private OpenAiAgentNextActionResponse.InvestmentDecision decision(
            InvestmentAction action,
            String symbol,
            Long quantity,
            Long expectedPriceKrw
    ) {
        return new OpenAiAgentNextActionResponse.InvestmentDecision(
                action,
                symbol,
                quantity,
                expectedPriceKrw,
                "Use the available evidence."
        );
    }

    private static Stream<OpenAiAgentNextActionResponse.InvestmentDecision>
            holdDecisionsWithOrderFields() {
        return Stream.of(
                new OpenAiAgentNextActionResponse.InvestmentDecision(
                        InvestmentAction.HOLD,
                        "005930",
                        null,
                        null,
                        "Hold with symbol."
                ),
                new OpenAiAgentNextActionResponse.InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        1L,
                        null,
                        "Hold with quantity."
                ),
                new OpenAiAgentNextActionResponse.InvestmentDecision(
                        InvestmentAction.HOLD,
                        null,
                        null,
                        70_000L,
                        "Hold with expected price."
                )
        );
    }
}
