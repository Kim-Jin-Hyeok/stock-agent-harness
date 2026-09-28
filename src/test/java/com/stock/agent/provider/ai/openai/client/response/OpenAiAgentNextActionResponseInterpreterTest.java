package com.stock.agent.provider.ai.openai.client.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.InvestmentAction;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponse;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiAgentNextActionResponseInterpreterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiResponsesOutputTextExtractor outputTextExtractor =
            mock(OpenAiResponsesOutputTextExtractor.class);
    private final OpenAiAgentNextActionResponseInterpreter interpreter =
            new OpenAiAgentNextActionResponseInterpreter(
                    objectMapper,
                    outputTextExtractor
            );
    private final OpenAiResponsesResponse response =
            mock(OpenAiResponsesResponse.class);

    @Test
    void interpretsRequestToolResponse() {
        when(outputTextExtractor.extract(response)).thenReturn("""
                {
                  "type": "REQUEST_TOOL",
                  "toolRequest": {
                    "type": "GET_CURRENT_PRICE",
                    "symbol": "005930"
                  },
                  "investmentDecision": null
                }
                """);

        OpenAiAgentNextActionResponse result =
                interpreter.interpret(response);

        assertThat(result.type())
                .isEqualTo(AgentNextActionType.REQUEST_TOOL);
        assertThat(result.toolRequest().type())
                .isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(result.toolRequest().symbol()).isEqualTo("005930");
        assertThat(result.investmentDecision()).isNull();
    }

    @Test
    void interpretsFinalDecisionResponse() {
        when(outputTextExtractor.extract(response)).thenReturn("""
                {
                  "type": "FINAL_DECISION",
                  "toolRequest": null,
                  "investmentDecision": {
                    "action": "BUY",
                    "symbol": "005930",
                    "quantity": 3,
                    "expectedPriceKrw": 75000,
                    "reason": "Moving average signal supports buying."
                  }
                }
                """);

        OpenAiAgentNextActionResponse result =
                interpreter.interpret(response);

        assertThat(result.type())
                .isEqualTo(AgentNextActionType.FINAL_DECISION);
        assertThat(result.toolRequest()).isNull();
        assertThat(result.investmentDecision().action())
                .isEqualTo(InvestmentAction.BUY);
        assertThat(result.investmentDecision().symbol())
                .isEqualTo("005930");
        assertThat(result.investmentDecision().quantity()).isEqualTo(3L);
        assertThat(result.investmentDecision().expectedPriceKrw())
                .isEqualTo(75000L);
        assertThat(result.investmentDecision().reason())
                .isEqualTo("Moving average signal supports buying.");
    }

    @Test
    void rejectsMalformedOutputText() {
        when(outputTextExtractor.extract(response)).thenReturn("not-json");

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "Failed to parse OpenAI agent action response."
                )
                .withCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void propagatesOutputExtractionFailure() {
        when(outputTextExtractor.extract(response))
                .thenThrow(new IllegalStateException("extraction failed"));

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage("extraction failed");
    }

    @Test
    void rejectsNullObjectMapper() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new OpenAiAgentNextActionResponseInterpreter(
                                null,
                                outputTextExtractor
                        )
                )
                .withMessage("objectMapper must not be null.");
    }

    @Test
    void rejectsNullOutputTextExtractor() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new OpenAiAgentNextActionResponseInterpreter(
                                objectMapper,
                                null
                        )
                )
                .withMessage("outputTextExtractor must not be null.");
    }
}
