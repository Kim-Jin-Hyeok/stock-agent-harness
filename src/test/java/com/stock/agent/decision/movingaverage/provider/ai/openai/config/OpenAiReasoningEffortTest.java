package com.stock.agent.decision.movingaverage.provider.ai.openai.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiReasoningEffortTest {

    @ParameterizedTest
    @CsvSource({
            "NONE, none",
            "LOW, low",
            "MEDIUM, medium",
            "HIGH, high",
            "XHIGH, xhigh",
            "MAX, max"
    })
    void convertsToOpenAiApiValue(
            OpenAiReasoningEffort effort,
            String expected
    ) {
        assertThat(effort.apiValue()).isEqualTo(expected);
    }
}
