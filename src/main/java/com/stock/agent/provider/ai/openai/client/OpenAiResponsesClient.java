package com.stock.agent.provider.ai.openai.client;

import com.stock.agent.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Slf4j
public class OpenAiResponsesClient {
    private static final String RESPONSES_PATH = "/v1/responses";

    private final RestClient restClient;
    private final String apiKey;
    private final Clock clock;

    public OpenAiResponsesClient(
            RestClient restClient,
            String apiKey,
            Clock clock
    ) {
        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null."
        );
        this.apiKey = requireText(apiKey, "apiKey");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    public OpenAiResponsesResponse createResponse(
            OpenAiResponsesRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        Instant startedAt = clock.instant();
        try {
            OpenAiResponsesResponse response = restClient.post()
                    .uri(RESPONSES_PATH)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + apiKey
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OpenAiResponsesResponse.class);

            if (response == null) {
                throw new IllegalStateException(
                        "OpenAI response body must not be null."
                );
            }
            logResponse(startedAt, response);
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                    "OpenAI Responses request failed. "
                            + "durationMs={}, failureType={}",
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName()
            );
            throw exception;
        }
    }

    private void logResponse(
            Instant startedAt,
            OpenAiResponsesResponse response
    ) {
        OpenAiResponsesResponse.Usage usage = response.usage();
        log.info(
                "OpenAI Responses request completed. "
                        + "responseId={}, model={}, status={}, durationMs={}, "
                        + "inputTokens={}, cachedInputTokens={}, "
                        + "outputTokens={}, reasoningTokens={}, totalTokens={}",
                response.id(),
                response.model(),
                response.status(),
                elapsedMillis(startedAt),
                usage == null ? null : usage.inputTokens(),
                cachedInputTokens(usage),
                usage == null ? null : usage.outputTokens(),
                reasoningTokens(usage),
                usage == null ? null : usage.totalTokens()
        );
    }

    private Long cachedInputTokens(OpenAiResponsesResponse.Usage usage) {
        if (usage == null || usage.inputTokensDetails() == null) {
            return null;
        }
        return usage.inputTokensDetails().cachedTokens();
    }

    private Long reasoningTokens(OpenAiResponsesResponse.Usage usage) {
        if (usage == null || usage.outputTokensDetails() == null) {
            return null;
        }
        return usage.outputTokensDetails().reasoningTokens();
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, clock.instant()).toMillis();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
