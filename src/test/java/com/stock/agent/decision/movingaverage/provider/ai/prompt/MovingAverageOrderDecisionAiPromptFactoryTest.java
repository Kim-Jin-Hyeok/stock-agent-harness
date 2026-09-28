package com.stock.agent.decision.movingaverage.provider.ai.prompt;

import com.stock.agent.InvestmentAction;
import com.stock.agent.decision.movingaverage.provider.ai.request.MovingAverageOrderDecisionAiRequest;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovingAverageOrderDecisionAiPromptFactoryTest {
    private final MovingAverageOrderDecisionAiPromptFactory factory =
            new MovingAverageOrderDecisionAiPromptFactory();

    @Test
    void createsPromptWithFixedDecisionRulesAndRequest() {
        MovingAverageOrderDecisionAiRequest request = request();

        MovingAverageOrderDecisionAiPrompt prompt = factory.create(request);
        String normalizedInstruction = prompt.systemInstruction()
                .replaceAll("\\s+", " ");

        assertThat(prompt.request()).isSameAs(request);
        assertThat(normalizedInstruction)
                .contains("Use only the data provided in the request.")
                .contains(
                        "Treat signalAction, symbol, and currentPriceKrw "
                                + "as fixed values."
                )
                .contains("maxAllowedQuantity, inclusive.")
                .contains(
                        "Return HOLD with a null quantity when signalAction "
                                + "is HOLD"
                )
                .contains("Do not infer or invent market data")
                .contains("intent, quantity, and reason.")
                .contains("EXECUTE_ORDER or HOLD");
    }

    @Test
    void createsSameSystemInstructionForSameContract() {
        MovingAverageOrderDecisionAiRequest request = request();

        MovingAverageOrderDecisionAiPrompt first = factory.create(request);
        MovingAverageOrderDecisionAiPrompt second = factory.create(request);

        assertThat(first.systemInstruction())
                .isEqualTo(second.systemInstruction());
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null.");
    }

    @Test
    void rejectsBlankSystemInstruction() {
        assertThatThrownBy(() -> new MovingAverageOrderDecisionAiPrompt(
                " ",
                request()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("systemInstruction must not be blank.");
    }

    private MovingAverageOrderDecisionAiRequest request() {
        return new MovingAverageOrderDecisionAiRequest(
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
        );
    }
}
