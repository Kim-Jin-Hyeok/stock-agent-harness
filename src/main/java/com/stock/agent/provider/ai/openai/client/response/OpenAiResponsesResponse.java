package com.stock.agent.provider.ai.openai.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponsesResponse(
        String id,
        String model,
        String status,
        @JsonProperty("incomplete_details")
        IncompleteDetails incompleteDetails,
        List<OutputItem> output,
        Usage usage
) {
    public OpenAiResponsesResponse {
        output = output == null ? List.of() : List.copyOf(output);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncompleteDetails(String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens")
            Long inputTokens,
            @JsonProperty("input_tokens_details")
            InputTokensDetails inputTokensDetails,
            @JsonProperty("output_tokens")
            Long outputTokens,
            @JsonProperty("output_tokens_details")
            OutputTokensDetails outputTokensDetails,
            @JsonProperty("total_tokens")
            Long totalTokens
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InputTokensDetails(
            @JsonProperty("cached_tokens")
            Long cachedTokens
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputTokensDetails(
            @JsonProperty("reasoning_tokens")
            Long reasoningTokens
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(
            String type,
            List<Content> content
    ) {
        public OutputItem {
            content = content == null ? List.of() : List.copyOf(content);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            String type,
            String text,
            String refusal
    ) {
    }
}
