package com.stock.agent.provider.ai.openai.execution;

import com.stock.agent.AgentNextAction;
import com.stock.agent.AgentNextActionType;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.agent.provider.ai.openai.client.request.OpenAiAgentNextActionRequestFactory;
import com.stock.agent.provider.ai.openai.client.request.OpenAiResponsesRequest;
import com.stock.agent.provider.ai.openai.client.response.OpenAiAgentNextActionResponseInterpreter;
import com.stock.agent.provider.ai.openai.client.response.OpenAiResponsesResponse;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponse;
import com.stock.agent.provider.ai.openai.response.OpenAiAgentNextActionResponseMapper;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
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

class OpenAiAgentNextActionExecutorTest {
    private final OpenAiAgentNextActionRequestFactory requestFactory = mock(
            OpenAiAgentNextActionRequestFactory.class
    );
    private final OpenAiResponsesClient client = mock(
            OpenAiResponsesClient.class
    );
    private final OpenAiAgentNextActionResponseInterpreter
            responseInterpreter = mock(
                    OpenAiAgentNextActionResponseInterpreter.class
            );
    private final OpenAiAgentNextActionResponseMapper responseMapper = mock(
            OpenAiAgentNextActionResponseMapper.class
    );

    private OpenAiAgentNextActionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OpenAiAgentNextActionExecutor(
                requestFactory,
                client,
                responseInterpreter,
                responseMapper
        );
    }

    @Test
    void executesOpenAiAgentNextActionFlow() {
        AgentNextActionAiPrompt prompt = mock(
                AgentNextActionAiPrompt.class
        );
        OpenAiResponsesRequest request = request();
        OpenAiResponsesResponse response = response();
        OpenAiAgentNextActionResponse interpretedResponse =
                new OpenAiAgentNextActionResponse(
                        AgentNextActionType.REQUEST_TOOL,
                        new OpenAiAgentNextActionResponse.ToolRequest(
                                HarnessToolType.GET_CURRENT_PRICE,
                                "005930"
                        ),
                        null
                );
        AgentNextAction action = AgentNextAction.requestTool(
                new HarnessToolRequest(
                        HarnessToolType.GET_CURRENT_PRICE,
                        "005930"
                )
        );
        when(requestFactory.create(prompt)).thenReturn(request);
        when(client.createResponse(request)).thenReturn(response);
        when(responseInterpreter.interpret(response))
                .thenReturn(interpretedResponse);
        when(responseMapper.map(interpretedResponse)).thenReturn(action);

        AgentNextAction result = executor.execute(prompt);

        assertThat(result).isSameAs(action);
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
        AgentNextActionAiPrompt prompt = mock(
                AgentNextActionAiPrompt.class
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
                .isThrownBy(() -> new OpenAiAgentNextActionExecutor(
                        null,
                        client,
                        responseInterpreter,
                        responseMapper
                ))
                .withMessage("requestFactory must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiAgentNextActionExecutor(
                        requestFactory,
                        null,
                        responseInterpreter,
                        responseMapper
                ))
                .withMessage("client must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiAgentNextActionExecutor(
                        requestFactory,
                        client,
                        null,
                        responseMapper
                ))
                .withMessage("responseInterpreter must not be null.");
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiAgentNextActionExecutor(
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
                "{\"candidateSymbols\":[\"005930\"]}",
                new OpenAiResponsesRequest.Reasoning("low"),
                512,
                new OpenAiResponsesRequest.Text(
                        new OpenAiResponsesRequest.Format(
                                "json_schema",
                                "agent_next_action",
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
