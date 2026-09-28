package com.stock.agent.decision.movingaverage.provider.ai.openai.client.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public record OpenAiResponsesRequest(
        String model,
        String instructions,
        String input,
        Reasoning reasoning,
        @JsonProperty("max_output_tokens") int maxOutputTokens,
        Text text,
        boolean store
) {
    public OpenAiResponsesRequest {
        model = requireText(model, "model");
        instructions = requireText(instructions, "instructions");
        input = requireText(input, "input");
        Objects.requireNonNull(reasoning, "reasoning must not be null.");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxOutputTokens must be positive."
            );
        }
        Objects.requireNonNull(text, "text must not be null.");
    }

    public record Reasoning(String effort) {
        public Reasoning {
            effort = requireText(effort, "effort");
        }
    }

    public record Text(Format format) {
        public Text {
            Objects.requireNonNull(format, "format must not be null.");
        }
    }

    public record Format(
            String type,
            String name,
            boolean strict,
            Map<String, Object> schema
    ) {
        public Format {
            type = requireText(type, "type");
            name = requireText(name, "name");
            Objects.requireNonNull(schema, "schema must not be null.");
            schema = Map.copyOf(schema);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
