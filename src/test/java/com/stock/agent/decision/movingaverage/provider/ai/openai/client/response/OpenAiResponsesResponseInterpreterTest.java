package com.stock.agent.decision.movingaverage.provider.ai.openai.client.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponse;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiResponsesResponseInterpreterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiResponsesResponseInterpreter interpreter =
            new OpenAiResponsesResponseInterpreter(objectMapper);

    @Test
    void interpretsCompletedStructuredOutput() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "id": "resp_123",
                  "status": "completed",
                  "incomplete_details": null,
                  "output": [
                    {
                      "type": "reasoning",
                      "summary": []
                    },
                    {
                      "type": "message",
                      "status": "completed",
                      "content": [
                        {
                          "type": "output_text",
                          "annotations": [],
                          "text": "{\\\"intent\\\":\\\"EXECUTE_ORDER\\\",\\\"quantity\\\":3,\\\"reason\\\":\\\"Signal and capacity support the order.\\\"}"
                        }
                      ]
                    }
                  ]
                }
                """);

        OpenAiMovingAverageOrderDecisionResponse result =
                interpreter.interpret(response);

        assertThat(result.intent())
                .isEqualTo(OrderDecisionIntent.EXECUTE_ORDER);
        assertThat(result.quantity()).isEqualTo(3L);
        assertThat(result.reason())
                .isEqualTo("Signal and capacity support the order.");
    }

    @Test
    void rejectsIncompleteResponse() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "incomplete",
                  "incomplete_details": {
                    "reason": "max_output_tokens"
                  },
                  "output": []
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "OpenAI response was incomplete. "
                                + "reason=max_output_tokens"
                );
    }

    @Test
    void rejectsRefusalInsteadOfTreatingItAsHold() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "refusal",
                          "refusal": "Unable to provide this decision."
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "OpenAI response was refused. reason="
                                + "Unable to provide this decision."
                );
    }

    @Test
    void rejectsCompletedResponseWithoutOutputText() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": []
                    }
                  ]
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "OpenAI response must contain exactly one "
                                + "output_text. count=0"
                );
    }

    @Test
    void rejectsMultipleOutputTexts() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{}"
                        },
                        {
                          "type": "output_text",
                          "text": "{}"
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "OpenAI response must contain exactly one "
                                + "output_text. count=2"
                );
    }

    @Test
    void rejectsMalformedOutputText() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "not-json"
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "Failed to parse OpenAI order decision response."
                )
                .withCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void rejectsUnknownStatus() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "queued",
                  "output": []
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> interpreter.interpret(response))
                .withMessage(
                        "OpenAI response was not completed. status=queued"
                );
    }

    @Test
    void rejectsNullResponse() {
        assertThatNullPointerException()
                .isThrownBy(() -> interpreter.interpret(null))
                .withMessage("response must not be null.");
    }

    private OpenAiResponsesResponse response(String json) throws Exception {
        return objectMapper.readValue(json, OpenAiResponsesResponse.class);
    }
}
