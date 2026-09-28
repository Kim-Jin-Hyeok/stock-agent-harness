package com.stock.agent.provider.ai.openai.execution;

import com.stock.agent.AgentNextAction;
import com.stock.agent.provider.ai.execution.AgentNextActionAiExecutor;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.provider.ai.openai.client.request.OpenAiAgentNextActionRequestFactory;
import com.stock.agent.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.provider.ai.openai.client.response.OpenAiAgentNextActionResponseInterpreter;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponse;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponseMapper;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;

import java.util.Objects;

public class OpenAiAgentNextActionExecutor
        implements AgentNextActionAiExecutor {
    private final OpenAiAgentNextActionRequestFactory requestFactory;
    private final OpenAiResponsesClient client;
    private final OpenAiAgentNextActionResponseInterpreter
            responseInterpreter;
    private final OpenAiAgentNextActionResponseMapper responseMapper;

    public OpenAiAgentNextActionExecutor(
            OpenAiAgentNextActionRequestFactory requestFactory,
            OpenAiResponsesClient client,
            OpenAiAgentNextActionResponseInterpreter responseInterpreter,
            OpenAiAgentNextActionResponseMapper responseMapper
    ) {
        this.requestFactory = Objects.requireNonNull(
                requestFactory,
                "requestFactory must not be null."
        );
        this.client = Objects.requireNonNull(
                client,
                "client must not be null."
        );
        this.responseInterpreter = Objects.requireNonNull(
                responseInterpreter,
                "responseInterpreter must not be null."
        );
        this.responseMapper = Objects.requireNonNull(
                responseMapper,
                "responseMapper must not be null."
        );
    }

    @Override
    public AgentNextAction execute(AgentNextActionAiPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null.");

        OpenAiResponsesRequest request = Objects.requireNonNull(
                requestFactory.create(prompt),
                "OpenAI request must not be null."
        );
        OpenAiResponsesResponse response = client.createResponse(request);
        OpenAiAgentNextActionResponse interpretedResponse =
                responseInterpreter.interpret(response);
        return responseMapper.map(interpretedResponse);
    }
}
