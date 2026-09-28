package com.stock.agent.provider.ai.openai.client.request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.provider.ai.openai.client.schema.OpenAiAgentNextActionResponseSchema;
import com.stock.agent.provider.ai.openai.config.OpenAiProviderProperties;
import com.stock.agent.provider.ai.openai.config.OpenAiReasoningEffort;
import com.stock.agent.provider.ai.prompt.AgentNextActionAiPrompt;
import com.stock.agent.provider.ai.request.AgentNextActionAiRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiAgentNextActionRequestFactoryTest {
    private static final String API_KEY = "secret-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();
    private final OpenAiProviderProperties properties = properties();
    private final OpenAiAgentNextActionResponseSchema responseSchema =
            new OpenAiAgentNextActionResponseSchema();
    private final OpenAiAgentNextActionRequestFactory factory =
            new OpenAiAgentNextActionRequestFactory(
                    objectMapper,
                    properties,
                    responseSchema
            );

    @Test
    void createsStructuredAgentNextActionRequest() throws Exception {
        AgentNextActionAiPrompt prompt = prompt();

        OpenAiResponsesRequest request = factory.create(prompt);
        JsonNode input = objectMapper.readTree(request.input());
        JsonNode serializedRequest = objectMapper.valueToTree(request);

        assertThat(request.model()).isEqualTo("gpt-6-luna");
        assertThat(request.instructions())
                .isEqualTo(prompt.systemInstruction());
        assertThat(input.path("strategyId").asText())
                .isEqualTo("DAY_TRADING_V1");
        assertThat(input.path("allowedToolTypes").get(0).asText())
                .isEqualTo("GET_CURRENT_PRICE");
        assertThat(input.path("candidateSymbols").get(0).asText())
                .isEqualTo("005930");
        assertThat(input.path("portfolioSnapshot")
                .path("cashAmountKrw").asLong()).isEqualTo(10_000_000L);
        assertThat(input.has("systemInstruction")).isFalse();
        assertThat(request.reasoning().effort()).isEqualTo("low");
        assertThat(serializedRequest.path("max_output_tokens").asInt())
                .isEqualTo(512);
        assertThat(serializedRequest.at("/text/format/type").asText())
                .isEqualTo("json_schema");
        assertThat(serializedRequest.at("/text/format/name").asText())
                .isEqualTo("agent_next_action");
        assertThat(serializedRequest.at("/text/format/strict").asBoolean())
                .isTrue();
        assertThat(serializedRequest.at("/text/format/schema/type").asText())
                .isEqualTo("object");
        assertThat(request.store()).isFalse();
    }

    @Test
    void doesNotExposeApiKey() throws Exception {
        OpenAiResponsesRequest request = factory.create(prompt());

        String serializedRequest = objectMapper.writeValueAsString(request);

        assertThat(serializedRequest).doesNotContain(API_KEY);
    }

    @Test
    void rejectsNullPrompt() {
        assertThatNullPointerException()
                .isThrownBy(() -> factory.create(null))
                .withMessage("prompt must not be null.");
    }

    @Test
    void wrapsInputSerializationFailure() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(
                any(AgentNextActionAiRequest.class)
        )).thenThrow(new JsonProcessingException("serialization failed") {
        });
        OpenAiAgentNextActionRequestFactory failingFactory =
                new OpenAiAgentNextActionRequestFactory(
                        failingObjectMapper,
                        properties,
                        responseSchema
                );

        assertThatIllegalStateException()
                .isThrownBy(() -> failingFactory.create(prompt()))
                .withMessage("Failed to serialize agent next action input.")
                .withCauseInstanceOf(JsonProcessingException.class);
    }

    private OpenAiProviderProperties properties() {
        return new OpenAiProviderProperties(
                URI.create("https://api.openai.com"),
                API_KEY,
                "gpt-6-luna",
                OpenAiReasoningEffort.LOW,
                Duration.ofSeconds(15),
                512
        );
    }

    private AgentNextActionAiPrompt prompt() {
        return new AgentNextActionAiPrompt(
                "Choose the next investment agent action.",
                new AgentNextActionAiRequest(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING,
                        List.of(
                                HarnessToolType.GET_CURRENT_PRICE,
                                HarnessToolType.GET_DAILY_PRICE_HISTORY
                        ),
                        List.of("005930"),
                        new PortfolioSnapshot(
                                10_000_000L,
                                10_000_000L,
                                List.of()
                        ),
                        new MarketSnapshot(
                                "KR",
                                true,
                                "Korean market is open."
                        ),
                        List.of()
                )
        );
    }
}
