package com.stock.agent.decision.movingaverage.provider.ai.openai.client.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.schema.OpenAiOrderDecisionResponseSchema;
import com.stock.agent.decision.movingaverage.provider.ai.openai.config.OpenAiMovingAverageOrderDecisionProperties;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OpenAiResponsesRequestFactory {
    private static final String RESPONSE_FORMAT_TYPE = "json_schema";
    private static final String RESPONSE_FORMAT_NAME =
            "moving_average_order_decision";

    private final ObjectMapper objectMapper;
    private final OpenAiMovingAverageOrderDecisionProperties properties;
    private final OpenAiOrderDecisionResponseSchema responseSchema;

    public OpenAiResponsesRequestFactory(
            ObjectMapper objectMapper,
            OpenAiMovingAverageOrderDecisionProperties properties,
            OpenAiOrderDecisionResponseSchema responseSchema
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

    public OpenAiResponsesRequest create(
            MovingAverageOrderDecisionAiPrompt prompt
    ) {
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

    private String serializeInput(
            MovingAverageOrderDecisionAiPrompt prompt
    ) {
        try {
            return objectMapper.writeValueAsString(prompt.request());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize moving average order decision input.",
                    exception
            );
        }
    }
}
