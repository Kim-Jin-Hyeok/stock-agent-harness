package com.stock.agent.provider.ai.openai.client.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiResponsesOutputTextExtractorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiResponsesOutputTextExtractor extractor =
            new OpenAiResponsesOutputTextExtractor();

    @Test
    void extractsOutputTextFromCompletedResponse() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "reasoning",
                      "summary": []
                    },
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\\"action\\\":\\\"FINISH\\\"}"
                        }
                      ]
                    }
                  ]
                }
                """);

        String outputText = extractor.extract(response);

        assertThat(outputText).isEqualTo("{\"action\":\"FINISH\"}");
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
                .isThrownBy(() -> extractor.extract(response))
                .withMessage(
                        "OpenAI response was incomplete. "
                                + "reason=max_output_tokens"
                );
    }

    @Test
    void rejectsRefusal() throws Exception {
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
                .isThrownBy(() -> extractor.extract(response))
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
                .isThrownBy(() -> extractor.extract(response))
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
                .isThrownBy(() -> extractor.extract(response))
                .withMessage(
                        "OpenAI response must contain exactly one "
                                + "output_text. count=2"
                );
    }

    @Test
    void rejectsBlankOutputText() throws Exception {
        OpenAiResponsesResponse response = response("""
                {
                  "status": "completed",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": " "
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> extractor.extract(response))
                .withMessage("OpenAI output_text must not be blank.");
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
                .isThrownBy(() -> extractor.extract(response))
                .withMessage(
                        "OpenAI response was not completed. status=queued"
                );
    }

    @Test
    void rejectsNullResponse() {
        assertThatNullPointerException()
                .isThrownBy(() -> extractor.extract(null))
                .withMessage("response must not be null.");
    }

    private OpenAiResponsesResponse response(String json) throws Exception {
        return objectMapper.readValue(json, OpenAiResponsesResponse.class);
    }
}
