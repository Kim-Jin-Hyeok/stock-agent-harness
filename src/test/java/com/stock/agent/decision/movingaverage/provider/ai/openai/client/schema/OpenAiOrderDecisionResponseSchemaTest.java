package com.stock.agent.decision.movingaverage.provider.ai.openai.client.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiOrderDecisionResponseSchemaTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiOrderDecisionResponseSchema responseSchema =
            new OpenAiOrderDecisionResponseSchema();

    @Test
    void createsStrictOrderDecisionSchema() {
        JsonNode schema = objectMapper.valueToTree(responseSchema.value());

        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required"))
                .containsExactly(
                        objectMapper.valueToTree("intent"),
                        objectMapper.valueToTree("quantity"),
                        objectMapper.valueToTree("reason")
                );
        assertThat(schema.at("/properties/intent/enum"))
                .containsExactly(
                        objectMapper.valueToTree("EXECUTE_ORDER"),
                        objectMapper.valueToTree("HOLD")
                );
        assertThat(schema.at("/properties/quantity/anyOf/0/type").asText())
                .isEqualTo("integer");
        assertThat(schema.at("/properties/quantity/anyOf/1/type").asText())
                .isEqualTo("null");
        assertThat(schema.at("/properties/reason/type").asText())
                .isEqualTo("string");
    }
}
