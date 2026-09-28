package com.stock.agent.provider.ai.openai.client.schema;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiAgentNextActionResponseSchema {
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "type", Map.of(
                            "type", "string",
                            "enum", List.of(
                                    "REQUEST_TOOL",
                                    "FINAL_DECISION"
                            )
                    ),
                    "toolRequest", nullableObject(toolRequestSchema()),
                    "investmentDecision", nullableObject(
                            investmentDecisionSchema()
                    )
            ),
            "required", List.of(
                    "type",
                    "toolRequest",
                    "investmentDecision"
            ),
            "additionalProperties", false
    );

    public Map<String, Object> value() {
        return SCHEMA;
    }

    private static Map<String, Object> toolRequestSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "GET_PORTFOLIO",
                                        "GET_MARKET",
                                        "GET_CURRENT_PRICE",
                                        "GET_DAILY_PRICE_HISTORY"
                                )
                        ),
                        "symbol", nullableString()
                ),
                "required", List.of("type", "symbol"),
                "additionalProperties", false
        );
    }

    private static Map<String, Object> investmentDecisionSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("BUY", "SELL", "HOLD")
                        ),
                        "symbol", nullableString(),
                        "quantity", nullableInteger(),
                        "expectedPriceKrw", nullableInteger(),
                        "reason", Map.of("type", "string")
                ),
                "required", List.of(
                        "action",
                        "symbol",
                        "quantity",
                        "expectedPriceKrw",
                        "reason"
                ),
                "additionalProperties", false
        );
    }

    private static Map<String, Object> nullableObject(
            Map<String, Object> objectSchema
    ) {
        return Map.of(
                "anyOf", List.of(
                        objectSchema,
                        Map.of("type", "null")
                )
        );
    }

    private static Map<String, Object> nullableString() {
        return Map.of(
                "anyOf", List.of(
                        Map.of("type", "string"),
                        Map.of("type", "null")
                )
        );
    }

    private static Map<String, Object> nullableInteger() {
        return Map.of(
                "anyOf", List.of(
                        Map.of("type", "integer"),
                        Map.of("type", "null")
                )
        );
    }
}
