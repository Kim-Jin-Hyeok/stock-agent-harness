package com.stock.agent.decision.movingaverage.provider.ai.openai.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponsesResponse(
        String status,
        @JsonProperty("incomplete_details")
        IncompleteDetails incompleteDetails,
        List<OutputItem> output
) {
    public OpenAiResponsesResponse {
        output = output == null ? List.of() : List.copyOf(output);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncompleteDetails(String reason) {
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
