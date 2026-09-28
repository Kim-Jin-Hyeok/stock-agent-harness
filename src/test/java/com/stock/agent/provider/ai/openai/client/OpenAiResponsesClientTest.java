package com.stock.agent.provider.ai.openai.client;

import com.stock.agent.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiResponsesClientTest {
    private static final String BASE_URL = "https://api.openai.com";
    private static final String API_KEY = "test-openai-api-key";

    private MockRestServiceServer server;
    private OpenAiResponsesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiResponsesClient(builder.build(), API_KEY);
    }

    @Test
    void sendsResponsesRequestAndDeserializesResponse() {
        server.expect(requestTo(BASE_URL + "/v1/responses"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(header(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(header(
                        "Accept",
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(content().json("""
                        {
                          "model": "gpt-6-luna",
                          "instructions": "Use only the supplied data.",
                          "input": "{\\\"symbol\\\":\\\"005930\\\"}",
                          "reasoning": {
                            "effort": "low"
                          },
                          "max_output_tokens": 512,
                          "text": {
                            "format": {
                              "type": "json_schema",
                              "name": "moving_average_order_decision",
                              "strict": true,
                              "schema": {
                                "type": "object"
                              }
                            }
                          },
                          "store": false
                        }
                        """))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": "resp_123",
                                  "status": "completed",
                                  "output": [
                                    {
                                      "type": "message",
                                      "content": [
                                        {
                                          "type": "output_text",
                                          "text": "{\\\"intent\\\":\\\"HOLD\\\",\\\"quantity\\\":null,\\\"reason\\\":\\\"Evidence is insufficient.\\\"}"
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        OpenAiResponsesResponse response = client.createResponse(request());

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.output()).hasSize(1);
        assertThat(response.output().getFirst().type()).isEqualTo("message");
        assertThat(response.output().getFirst().content().getFirst().type())
                .isEqualTo("output_text");
        server.verify();
    }

    @Test
    void rejectsNullRestClient() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiResponsesClient(null, API_KEY))
                .withMessage("restClient must not be null.");
    }

    @Test
    void rejectsBlankApiKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenAiResponsesClient(
                        RestClient.create(BASE_URL),
                        " "
                ))
                .withMessage("apiKey must not be blank.");
    }

    @Test
    void rejectsNullRequest() {
        assertThatNullPointerException()
                .isThrownBy(() -> client.createResponse(null))
                .withMessage("request must not be null.");
    }

    @Test
    void rejectsEmptyResponseBody() {
        server.expect(requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThatIllegalStateException()
                .isThrownBy(() -> client.createResponse(request()))
                .withMessage("OpenAI response body must not be null.");

        server.verify();
    }

    private OpenAiResponsesRequest request() {
        return new OpenAiResponsesRequest(
                "gpt-6-luna",
                "Use only the supplied data.",
                "{\"symbol\":\"005930\"}",
                new OpenAiResponsesRequest.Reasoning("low"),
                512,
                new OpenAiResponsesRequest.Text(
                        new OpenAiResponsesRequest.Format(
                                "json_schema",
                                "moving_average_order_decision",
                                true,
                                Map.of("type", "object")
                        )
                ),
                false
        );
    }
}
