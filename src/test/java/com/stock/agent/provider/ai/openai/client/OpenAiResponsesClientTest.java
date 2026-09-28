package com.stock.agent.provider.ai.openai.client;

import com.stock.agent.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiResponsesClientTest {
    private static final String BASE_URL = "https://api.openai.com";
    private static final String API_KEY = "test-openai-api-key";
    private static final Instant REQUEST_STARTED_AT =
            Instant.parse("2026-09-28T13:15:00Z");
    private static final Instant REQUEST_FINISHED_AT =
            Instant.parse("2026-09-28T13:15:01.250Z");

    private MockRestServiceServer server;
    private OpenAiResponsesClient client;
    private Clock clock;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(
                REQUEST_STARTED_AT,
                REQUEST_FINISHED_AT
        );
        client = new OpenAiResponsesClient(
                builder.build(),
                API_KEY,
                clock
        );
    }

    @Test
    void sendsResponsesRequestAndDeserializesResponse(CapturedOutput output) {
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
                                  "model": "gpt-6-luna-2026-08-07",
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
                                  ],
                                  "usage": {
                                    "input_tokens": 100,
                                    "input_tokens_details": {
                                      "cached_tokens": 40
                                    },
                                    "output_tokens": 20,
                                    "output_tokens_details": {
                                      "reasoning_tokens": 8
                                    },
                                    "total_tokens": 120
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        OpenAiResponsesResponse response = client.createResponse(request());

        assertThat(response.id()).isEqualTo("resp_123");
        assertThat(response.model()).isEqualTo("gpt-6-luna-2026-08-07");
        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.output()).hasSize(1);
        assertThat(response.output().getFirst().type()).isEqualTo("message");
        assertThat(response.output().getFirst().content().getFirst().type())
                .isEqualTo("output_text");
        assertThat(response.usage().inputTokens()).isEqualTo(100L);
        assertThat(response.usage().inputTokensDetails().cachedTokens())
                .isEqualTo(40L);
        assertThat(response.usage().outputTokens()).isEqualTo(20L);
        assertThat(response.usage().outputTokensDetails().reasoningTokens())
                .isEqualTo(8L);
        assertThat(response.usage().totalTokens()).isEqualTo(120L);
        assertThat(output.getAll())
                .contains(
                        "OpenAI Responses request completed. "
                                + "responseId=resp_123, "
                                + "model=gpt-6-luna-2026-08-07, "
                                + "status=completed, durationMs=1250, "
                                + "inputTokens=100, cachedInputTokens=40, "
                                + "outputTokens=20, reasoningTokens=8, "
                                + "totalTokens=120"
                )
                .doesNotContain(API_KEY)
                .doesNotContain("Evidence is insufficient.");
        server.verify();
    }

    @Test
    void logsCompletedResponseWithoutUsage(CapturedOutput output) {
        server.expect(requestTo(BASE_URL + "/v1/responses"))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": "resp_without_usage",
                                  "model": "gpt-6-luna-2026-08-07",
                                  "status": "completed",
                                  "output": []
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        OpenAiResponsesResponse response = client.createResponse(request());

        assertThat(response.usage()).isNull();
        assertThat(output.getAll()).contains(
                "inputTokens=null, cachedInputTokens=null, "
                        + "outputTokens=null, reasoningTokens=null, "
                        + "totalTokens=null"
        );
        server.verify();
    }

    @Test
    void logsFailedRequestDurationWithoutSecrets(CapturedOutput output) {
        server.expect(method(POST)).andRespond(withServerError());

        assertThatThrownBy(() -> client.createResponse(request()))
                .isInstanceOf(RestClientResponseException.class);

        assertThat(output.getAll())
                .contains(
                        "OpenAI Responses request failed. "
                                + "durationMs=1250, "
                                + "failureType=InternalServerError"
                )
                .doesNotContain(API_KEY)
                .doesNotContain("Use only the supplied data.");
        server.verify();
    }

    @Test
    void rejectsNullRestClient() {
        assertThatNullPointerException().isThrownBy(() ->
                        new OpenAiResponsesClient(null, API_KEY, clock)
                )
                .withMessage("restClient must not be null.");
    }

    @Test
    void rejectsBlankApiKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenAiResponsesClient(
                        RestClient.create(BASE_URL),
                        " ",
                        clock
                ))
                .withMessage("apiKey must not be blank.");
    }

    @Test
    void rejectsNullClock() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiResponsesClient(
                        RestClient.create(BASE_URL),
                        API_KEY,
                        null
                ))
                .withMessage("clock must not be null.");
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
