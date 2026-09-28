package com.stock.agent.provider.ai.openai.client.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OpenAiAgentNextActionResponseInterpreter {
    private final ObjectMapper objectMapper;
    private final OpenAiResponsesOutputTextExtractor outputTextExtractor;

    public OpenAiAgentNextActionResponseInterpreter(
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

    public OpenAiAgentNextActionResponse interpret(
            OpenAiResponsesResponse response
    ) {
        String outputText = outputTextExtractor.extract(response);
        return parseAgentAction(outputText);
    }

    private OpenAiAgentNextActionResponse parseAgentAction(
            String outputText
    ) {
        try {
            return objectMapper.readValue(
                    outputText,
                    OpenAiAgentNextActionResponse.class
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse OpenAI agent action response.",
                    exception
            );
        }
    }
}
