package com.stock.agent.decision.movingaverage.provider.ai.openai.execution;

import com.stock.agent.decision.movingaverage.provider.ai.openai.client.request.OpenAiResponsesRequestFactory;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.response.OpenAiResponsesResponseInterpreter;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponse;
import com.stock.agent.decision.movingaverage.provider.ai.openai.response.OpenAiMovingAverageOrderDecisionResponseMapper;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import com.stock.agent.decision.movingaverage.provider.result.MovingAverageOrderDecisionProviderResult;
import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenAiMovingAverageOrderDecisionExecutorTest {
    private final OpenAiResponsesRequestFactory requestFactory = mock(
            OpenAiResponsesRequestFactory.class
    );
    private final OpenAiResponsesClient client = mock(
            OpenAiResponsesClient.class
    );
    private final OpenAiResponsesResponseInterpreter responseInterpreter =
            mock(OpenAiResponsesResponseInterpreter.class);
    private final OpenAiMovingAverageOrderDecisionResponseMapper
            responseMapper = mock(
                    OpenAiMovingAverageOrderDecisionResponseMapper.class
            );

    private OpenAiMovingAverageOrderDecisionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OpenAiMovingAverageOrderDecisionExecutor(
                requestFactory,
                client,
                responseInterpreter,
                responseMapper
        );
    }

    @Test
    void executesOpenAiOrderDecisionFlow() {
        MovingAverageOrderDecisionAiPrompt prompt = mock(
                MovingAverageOrderDecisionAiPrompt.class
        );
        OpenAiResponsesRequest request = request();
        OpenAiResponsesResponse response = response();
        OpenAiMovingAverageOrderDecisionResponse interpretedResponse =
                new OpenAiMovingAverageOrderDecisionResponse(
                        OrderDecisionIntent.EXECUTE_ORDER,
                        3L,
                        "Execute three shares within the allowed capacity."
                );
        OrderQuantityProposal proposal = OrderQuantityProposal.execute(
                3L,
                interpretedResponse.reason()
        );
        when(requestFactory.create(prompt)).thenReturn(request);
        when(client.createResponse(request)).thenReturn(response);
        when(responseInterpreter.interpret(response))
                .thenReturn(interpretedResponse);
        when(responseMapper.map(interpretedResponse)).thenReturn(proposal);

        MovingAverageOrderDecisionProviderResult result = executor.execute(
                prompt
        );

        assertThat(result.proposal()).isSameAs(proposal);
        assertThat(result.providerIdentity().providerId())
                .isEqualTo("OPENAI_GPT_6_LUNA");
        assertThat(result.providerIdentity().providerVersion()).isEqualTo(1);
        InOrder executionOrder = inOrder(
                requestFactory,
                client,
                responseInterpreter,
                responseMapper
        );
        executionOrder.verify(requestFactory).create(prompt);
        executionOrder.verify(client).createResponse(request);
        executionOrder.verify(responseInterpreter).interpret(response);
        executionOrder.verify(responseMapper).map(interpretedResponse);
    }

    @Test
    void propagatesOpenAiClientFailure() {
        MovingAverageOrderDecisionAiPrompt prompt = mock(
                MovingAverageOrderDecisionAiPrompt.class
        );
        OpenAiResponsesRequest request = request();
        when(requestFactory.create(prompt)).thenReturn(request);
        when(client.createResponse(request)).thenThrow(
                new IllegalStateException("OpenAI request failed.")
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> executor.execute(prompt))
                .withMessage("OpenAI request failed.");

        verifyNoInteractions(responseInterpreter, responseMapper);
    }

    @Test
    void rejectsNullPrompt() {
        assertThatNullPointerException()
                .isThrownBy(() -> executor.execute(null))
                .withMessage("prompt must not be null.");

        verifyNoInteractions(
                requestFactory,
                client,
                responseInterpreter,
                responseMapper
        );
    }

    @Test
    void rejectsNullDependencies() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiMovingAverageOrderDecisionExecutor(
                        null,
                        client,
                        responseInterpreter,
                        responseMapper
                ))
                .withMessage("requestFactory must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiMovingAverageOrderDecisionExecutor(
                        requestFactory,
                        null,
                        responseInterpreter,
                        responseMapper
                ))
                .withMessage("client must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiMovingAverageOrderDecisionExecutor(
                        requestFactory,
                        client,
                        null,
                        responseMapper
                ))
                .withMessage("responseInterpreter must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiMovingAverageOrderDecisionExecutor(
                        requestFactory,
                        client,
                        responseInterpreter,
                        null
                ))
                .withMessage("responseMapper must not be null.");
    }

    private OpenAiResponsesRequest request() {
        return new OpenAiResponsesRequest(
                "gpt-6-luna",
                "Use only the supplied data.",
                "{\"symbol\":\"005930\"}",
                new OpenAiResponsesRequest.Reasoning("low"),
                512,
                new OpenAiResponsesRequest.Text(
                        new OpenAiResponsesRequest.Format(
                                "json_schema",
                                "moving_average_order_decision",
                                true,
                                Map.of("type", "object")
                        )
                ),
                false
        );
    }

    private OpenAiResponsesResponse response() {
        return new OpenAiResponsesResponse(
                "completed",
                null,
                List.of()
        );
    }
}
