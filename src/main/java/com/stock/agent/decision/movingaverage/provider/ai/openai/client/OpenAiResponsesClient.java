package com.stock.agent.decision.movingaverage.provider.ai.openai.client;

import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Objects;

public class OpenAiResponsesClient {
    private static final String RESPONSES_PATH = "/v1/responses";

    private final RestClient restClient;
    private final String apiKey;

    public OpenAiResponsesClient(
            RestClient restClient,
            String apiKey
    ) {
        this.restClient = Objects.requireNonNull(
                restClient,
                "restClient must not be null."
        );
        this.apiKey = requireText(apiKey, "apiKey");
    }

    public OpenAiResponsesResponse createResponse(
            OpenAiResponsesRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

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
        return response;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
