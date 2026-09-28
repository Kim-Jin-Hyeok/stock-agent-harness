package com.stock.agent.decision.movingaverage.provider.ai.openai.response;

import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiMovingAverageOrderDecisionResponseTest {

    @Test
    void createsResponseWithStructuredFields() {
        OpenAiMovingAverageOrderDecisionResponse response =
                new OpenAiMovingAverageOrderDecisionResponse(
                        OrderDecisionIntent.EXECUTE_ORDER,
                        3L,
                        "Signal and capacity support the order."
                );

        assertThat(response.intent())
                .isEqualTo(OrderDecisionIntent.EXECUTE_ORDER);
        assertThat(response.quantity()).isEqualTo(3L);
        assertThat(response.reason())
                .isEqualTo("Signal and capacity support the order.");
    }

    @Test
    void rejectsNullIntent() {
        assertThatNullPointerException()
                .isThrownBy(() -> new OpenAiMovingAverageOrderDecisionResponse(
                        null,
                        null,
                        "Wait for another signal."
                ))
                .withMessage("OpenAI response intent must not be null.");
    }

    @Test
    void rejectsBlankReason() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenAiMovingAverageOrderDecisionResponse(
                        OrderDecisionIntent.HOLD,
                        null,
                        " "
                ))
                .withMessage("OpenAI response reason must not be blank.");
    }
}
