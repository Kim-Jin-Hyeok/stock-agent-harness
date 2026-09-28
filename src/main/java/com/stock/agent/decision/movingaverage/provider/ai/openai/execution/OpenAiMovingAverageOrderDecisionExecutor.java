package com.stock.agent.decision.movingaverage.provider.ai.openai.execution;

import com.stock.agent.decision.movingaverage.provider.ai.execution.MovingAverageOrderDecisionAiExecutor;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequestFactory;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponse;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponseInterpreter;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponse;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponseMapper;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import com.stock.agent.decision.movingaverage.provider.identity.MovingAverageOrderDecisionProviderIdentity;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;

import java.util.Locale;
import java.util.Objects;

public class OpenAiMovingAverageOrderDecisionExecutor
        implements MovingAverageOrderDecisionAiExecutor {
    private static final int PROVIDER_VERSION = 1;

    private final OpenAiResponsesRequestFactory requestFactory;
    private final OpenAiResponsesClient client;
    private final OpenAiResponsesResponseInterpreter responseInterpreter;
    private final OpenAiMovingAverageOrderDecisionResponseMapper responseMapper;

    public OpenAiMovingAverageOrderDecisionExecutor(
            OpenAiResponsesRequestFactory requestFactory,
            OpenAiResponsesClient client,
            OpenAiResponsesResponseInterpreter responseInterpreter,
            OpenAiMovingAverageOrderDecisionResponseMapper responseMapper
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
    public MovingAverageOrderDecisionProviderResult execute(
            MovingAverageOrderDecisionAiPrompt prompt
    ) {
        Objects.requireNonNull(prompt, "prompt must not be null.");

        OpenAiResponsesRequest request = Objects.requireNonNull(
                requestFactory.create(prompt),
                "OpenAI request must not be null."
        );
        OpenAiResponsesResponse response = client.createResponse(request);
        OpenAiMovingAverageOrderDecisionResponse interpretedResponse =
                responseInterpreter.interpret(response);
        OrderQuantityProposal proposal = responseMapper.map(
                interpretedResponse
        );

        return new MovingAverageOrderDecisionProviderResult(
                providerIdentity(request.model()),
                proposal
        );
    }

    private MovingAverageOrderDecisionProviderIdentity providerIdentity(
            String model
    ) {
        String providerId = "OPENAI_"
                + model.toUpperCase(Locale.ROOT).replace('-', '_');
        return new MovingAverageOrderDecisionProviderIdentity(
                providerId,
                PROVIDER_VERSION
        );
    }
}
