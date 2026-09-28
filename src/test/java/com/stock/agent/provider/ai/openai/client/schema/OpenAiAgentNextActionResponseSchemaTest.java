package com.stock.agent.provider.ai.openai.client.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiAgentNextActionResponseSchemaTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiAgentNextActionResponseSchema responseSchema =
            new OpenAiAgentNextActionResponseSchema();

    @Test
    void createsStrictAgentNextActionSchema() {
        JsonNode schema = objectMapper.valueToTree(responseSchema.value());

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required"))
                .containsExactly(
                        objectMapper.valueToTree("type"),
                        objectMapper.valueToTree("toolRequest"),
                        objectMapper.valueToTree("investmentDecision")
                );
        assertThat(schema.at("/properties/type/enum"))
                .containsExactly(
                        objectMapper.valueToTree("REQUEST_TOOL"),
                        objectMapper.valueToTree("FINAL_DECISION")
                );
    }

    @Test
    void restrictsToolRequestToAllowedReadTools() {
        JsonNode schema = objectMapper.valueToTree(responseSchema.value());
        JsonNode toolRequest = schema.at(
                "/properties/toolRequest/anyOf/0"
        );

        assertThat(toolRequest.path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(toolRequest.path("required"))
                .containsExactly(
                        objectMapper.valueToTree("type"),
                        objectMapper.valueToTree("symbol")
                );
        assertThat(toolRequest.at("/properties/type/enum"))
                .containsExactly(
                        objectMapper.valueToTree("GET_PORTFOLIO"),
                        objectMapper.valueToTree("GET_MARKET"),
                        objectMapper.valueToTree("GET_CURRENT_PRICE"),
                        objectMapper.valueToTree("GET_DAILY_PRICE_HISTORY")
                );
        assertThat(toolRequest.at("/properties/symbol/anyOf/0/type").asText())
                .isEqualTo("string");
        assertThat(toolRequest.at("/properties/symbol/anyOf/1/type").asText())
                .isEqualTo("null");
        assertThat(schema.at("/properties/toolRequest/anyOf/1/type").asText())
                .isEqualTo("null");
    }

    @Test
    void restrictsFinalDecisionToInvestmentActions() {
        JsonNode schema = objectMapper.valueToTree(responseSchema.value());
        JsonNode decision = schema.at(
                "/properties/investmentDecision/anyOf/0"
        );

        assertThat(decision.path("additionalProperties").asBoolean())
                .isFalse();
        assertThat(decision.path("required"))
                .containsExactly(
                        objectMapper.valueToTree("action"),
                        objectMapper.valueToTree("symbol"),
                        objectMapper.valueToTree("quantity"),
                        objectMapper.valueToTree("expectedPriceKrw"),
                        objectMapper.valueToTree("reason")
                );
        assertThat(decision.at("/properties/action/enum"))
                .containsExactly(
                        objectMapper.valueToTree("BUY"),
                        objectMapper.valueToTree("SELL"),
                        objectMapper.valueToTree("HOLD")
                );
        assertThat(decision.at("/properties/quantity/anyOf/0/type").asText())
                .isEqualTo("integer");
        assertThat(decision.at("/properties/quantity/anyOf/1/type").asText())
                .isEqualTo("null");
        assertThat(decision.at("/properties/reason/type").asText())
                .isEqualTo("string");
        assertThat(schema.at(
                "/properties/investmentDecision/anyOf/1/type"
        ).asText()).isEqualTo("null");
    }
}
