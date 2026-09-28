package com.stock.agent.decision.movingaverage.provider.ai.openai.client.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponse;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesOutputTextExtractor;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OpenAiResponsesResponseInterpreter {
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesOutputTextExtractor outputTextExtractor;

    public OpenAiResponsesResponseInterpreter(
            ObjectMapper objectMapper,
            OpenAiResponsesOutputTextExtractor outputTextExtractor
    ) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null."
        );
        this.outputTextExtractor = Objects.requireNonNull(
                outputTextExtractor,
                "outputTextExtractor must not be null."
        );
    }

    public OpenAiMovingAverageOrderDecisionResponse interpret(
            OpenAiResponsesResponse response
    ) {
        String outputText = outputTextExtractor.extract(response);
        return parseOrderDecision(outputText);
    }

    private OpenAiMovingAverageOrderDecisionResponse parseOrderDecision(
            String outputText
    ) {
        try {
            return objectMapper.readValue(
                    outputText,
                    OpenAiMovingAverageOrderDecisionResponse.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse OpenAI order decision response.",
                    exception
            );
        }
    }

}
