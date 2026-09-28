package com.stock.agent.decision.movingaverage.provider.ai.openai.client.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.provider.ai.openai.client.schema.OpenAiOrderDecisionResponseSchema;
import com.stock.agent.decision.movingaverage.provider.ai.openai.config.OpenAiMovingAverageOrderDecisionProperties;
import com.stock.agent.decision.movingaverage.provider.ai.openai.config.OpenAiReasoningEffort;
import com.stock.agent.decision.movingaverage.provider.ai.prompt.MovingAverageOrderDecisionAiPrompt;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequest;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiResponsesRequestFactoryTest {
    private static final String API_KEY = "secret-api-key";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();
    private final OpenAiResponsesRequestFactory factory =
            new OpenAiResponsesRequestFactory(
                    objectMapper,
                    properties(),
                    new OpenAiOrderDecisionResponseSchema()
            );

    @Test
    void createsStructuredOpenAiResponsesRequest() throws Exception {
        MovingAverageOrderDecisionAiPrompt prompt = prompt();

        OpenAiResponsesRequest request = factory.create(prompt);
        JsonNode input = objectMapper.readTree(request.input());
        JsonNode serializedRequest = objectMapper.valueToTree(request);

        assertThat(request.model()).isEqualTo("gpt-6-luna");
        assertThat(request.instructions())
                .isEqualTo(prompt.systemInstruction());
        assertThat(input.path("strategyId").asText())
                .isEqualTo("DAY_TRADING_V1");
        assertThat(input.path("signalAction").asText()).isEqualTo("BUY");
        assertThat(input.path("symbol").asText()).isEqualTo("005930");
        assertThat(input.path("maxAllowedQuantity").asLong()).isEqualTo(10);
        assertThat(request.reasoning().effort()).isEqualTo("low");
        assertThat(serializedRequest.path("max_output_tokens").asInt())
                .isEqualTo(512);
        assertThat(serializedRequest.has("maxOutputTokens")).isFalse();
        assertThat(serializedRequest.at("/text/format/type").asText())
                .isEqualTo("json_schema");
        assertThat(serializedRequest.at("/text/format/name").asText())
                .isEqualTo("moving_average_order_decision");
        assertThat(serializedRequest.at("/text/format/strict").asBoolean())
                .isTrue();
        assertThat(serializedRequest.at("/text/format/schema/type").asText())
                .isEqualTo("object");
        assertThat(request.store()).isFalse();
        assertThat(serializedRequest.toString()).doesNotContain(API_KEY);
    }

    @Test
    void rejectsNullPrompt() {
        assertThatNullPointerException()
                .isThrownBy(() -> factory.create(null))
                .withMessage("prompt must not be null.");
    }

    private OpenAiMovingAverageOrderDecisionProperties properties() {
        return new OpenAiMovingAverageOrderDecisionProperties(
                URI.create("https://api.openai.com"),
                API_KEY,
                "gpt-6-luna",
                OpenAiReasoningEffort.LOW,
                Duration.ofSeconds(15),
                512
        );
    }

    private MovingAverageOrderDecisionAiPrompt prompt() {
        return new MovingAverageOrderDecisionAiPrompt(
                "Use only the provided investment data.",
                new MovingAverageOrderDecisionAiRequest(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING,
                        InvestmentAction.BUY,
                        "005930",
                        100_000L,
                        CurrentPriceLookupSource.PROVIDER,
                        Instant.parse("2026-09-28T00:00:00Z"),
                        10_000_000L,
                        10_000_000L,
                        0,
                        MovingAverageTrend.FLAT,
                        MovingAverageTrend.UPTREND,
                        MovingAverageCrossoverSignal.GOLDEN_CROSS,
                        5,
                        new BigDecimal("71000.00"),
                        20,
                        new BigDecimal("70000.00"),
                        LocalDate.of(2026, 9, 28),
                        100,
                        10,
                        30,
                        10,
                        new BigDecimal("0.010000")
                )
        );
    }
}
