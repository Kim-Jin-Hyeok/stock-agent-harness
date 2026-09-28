package com.stock.agent.provider.ai.openai.client.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.provider.ai.openai.client.schema.OpenAiAgentNextActionResponseSchema;
import com.stock.agent.provider.ai.openai.config.OpenAiProviderProperties;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OpenAiAgentNextActionRequestFactory {
    private static final String RESPONSE_FORMAT_TYPE = "json_schema";
    private static final String RESPONSE_FORMAT_NAME = "agent_next_action";

    private final ObjectMapper objectMapper;
    private final OpenAiProviderProperties properties;
    private final OpenAiAgentNextActionResponseSchema responseSchema;

    public OpenAiAgentNextActionRequestFactory(
            ObjectMapper objectMapper,
            OpenAiProviderProperties properties,
            OpenAiAgentNextActionResponseSchema responseSchema
    ) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null."
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null."
        );
        this.responseSchema = Objects.requireNonNull(
                responseSchema,
                "responseSchema must not be null."
        );
    }

    public OpenAiResponsesRequest create(AgentNextActionAiPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null.");
        return new OpenAiResponsesRequest(
                properties.model(),
                prompt.systemInstruction(),
                serializeInput(prompt),
                new OpenAiResponsesRequest.Reasoning(
                        properties.reasoningEffort().apiValue()
                ),
                properties.maxOutputTokens(),
                new OpenAiResponsesRequest.Text(
                        new OpenAiResponsesRequest.Format(
                                RESPONSE_FORMAT_TYPE,
                                RESPONSE_FORMAT_NAME,
                                true,
                                responseSchema.value()
                        )
                ),
                false
        );
    }

    private String serializeInput(AgentNextActionAiPrompt prompt) {
        try {
            return objectMapper.writeValueAsString(prompt.request());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize agent next action input.",
                    exception
            );
        }
    }
}
