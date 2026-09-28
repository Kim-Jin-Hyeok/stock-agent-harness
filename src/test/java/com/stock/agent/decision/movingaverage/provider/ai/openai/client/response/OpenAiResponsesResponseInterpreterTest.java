package com.stock.agent.decision.movingaverage.provider.ai.openai.client.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponse;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesOutputTextExtractor;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiResponsesResponseInterpreterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiResponsesResponseInterpreter interpreter =
            new OpenAiResponsesResponseInterpreter(
                    objectMapper,
                    new OpenAiResponsesOutputTextExtractor()
            );

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
    void propagatesOutputExtractionFailure() {
        OpenAiResponsesOutputTextExtractor outputTextExtractor =
                mock(OpenAiResponsesOutputTextExtractor.class);
        OpenAiResponsesResponse response = mock(OpenAiResponsesResponse.class);
        OpenAiResponsesResponseInterpreter responseInterpreter =
                new OpenAiResponsesResponseInterpreter(
                        objectMapper,
                        outputTextExtractor
                );
        when(outputTextExtractor.extract(response))
                .thenThrow(new IllegalStateException("extraction failed"));

        assertThatIllegalStateException()
                .isThrownBy(() -> responseInterpreter.interpret(response))
                .withMessage("extraction failed");
    }

    @Test
    void rejectsNullObjectMapper() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiResponsesResponseInterpreter(
                        null,
                        new OpenAiResponsesOutputTextExtractor()
                ))
                .withMessage("objectMapper must not be null.");
    }

    @Test
    void rejectsNullOutputTextExtractor() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiResponsesResponseInterpreter(
                        objectMapper,
                        null
                ))
                .withMessage("outputTextExtractor must not be null.");
    }

    private OpenAiResponsesResponse response(String json) throws Exception {
        return objectMapper.readValue(json, OpenAiResponsesResponse.class);
    }
}
