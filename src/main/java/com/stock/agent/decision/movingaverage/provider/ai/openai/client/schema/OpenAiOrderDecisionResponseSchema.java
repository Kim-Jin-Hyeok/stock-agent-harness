package com.stock.agent.decision.movingaverage.provider.ai.openai.client.schema;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiOrderDecisionResponseSchema {
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "intent", Map.of(
                            "type", "string",
                            "enum", List.of("EXECUTE_ORDER", "HOLD")
                    ),
                    "quantity", Map.of(
                            "anyOf", List.of(
                                    Map.of("type", "integer"),
                                    Map.of("type", "null")
                            )
                    ),
                    "reason", Map.of("type", "string")
            ),
            "required", List.of("intent", "quantity", "reason"),
            "additionalProperties", false
    );

    public Map<String, Object> value() {
        return SCHEMA;
    }
}
