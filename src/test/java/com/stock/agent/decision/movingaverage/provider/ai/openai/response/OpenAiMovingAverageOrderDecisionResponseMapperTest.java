package com.stock.agent.decision.movingaverage.provider.ai.openai.response;

import com.stock.agent.decision.order.proposal.OrderDecisionIntent;
import com.stock.agent.decision.order.proposal.OrderQuantityProposal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OpenAiMovingAverageOrderDecisionResponseMapperTest {
    private final OpenAiMovingAverageOrderDecisionResponseMapper mapper =
            new OpenAiMovingAverageOrderDecisionResponseMapper();

    @Test
    void mapsExecuteOrderResponseToProposal() {
        OpenAiMovingAverageOrderDecisionResponse response = response(
                OrderDecisionIntent.EXECUTE_ORDER,
                3L
        );

        OrderQuantityProposal proposal = mapper.map(response);

        assertThat(proposal.intent())
                .isEqualTo(OrderDecisionIntent.EXECUTE_ORDER);
        assertThat(proposal.quantity()).isEqualTo(3L);
        assertThat(proposal.reason()).isEqualTo(response.reason());
    }

    @Test
    void mapsHoldResponseToProposal() {
        OpenAiMovingAverageOrderDecisionResponse response = response(
                OrderDecisionIntent.HOLD,
                null
        );

        OrderQuantityProposal proposal = mapper.map(response);

        assertThat(proposal.intent()).isEqualTo(OrderDecisionIntent.HOLD);
        assertThat(proposal.quantity()).isNull();
        assertThat(proposal.reason()).isEqualTo(response.reason());
    }

    @Test
    void rejectsNullResponse() {
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.map(null))
                .withMessage("response must not be null.");
    }

    @Test
    void rejectsExecuteOrderWithoutQuantity() {
        OpenAiMovingAverageOrderDecisionResponse response = response(
                OrderDecisionIntent.EXECUTE_ORDER,
                null
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage("EXECUTE_ORDER quantity must be positive.");
    }

    @Test
    void rejectsExecuteOrderWithNonPositiveQuantity() {
        OpenAiMovingAverageOrderDecisionResponse response = response(
                OrderDecisionIntent.EXECUTE_ORDER,
                0L
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage("EXECUTE_ORDER quantity must be positive.");
    }

    @Test
    void rejectsHoldWithQuantity() {
        OpenAiMovingAverageOrderDecisionResponse response = response(
                OrderDecisionIntent.HOLD,
                1L
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> mapper.map(response))
                .withMessage("HOLD quantity must be null.");
    }

    private OpenAiMovingAverageOrderDecisionResponse response(
            OrderDecisionIntent intent,
            Long quantity
    ) {
        return new OpenAiMovingAverageOrderDecisionResponse(
                intent,
                quantity,
                "Decision grounded in the provided moving-average data."
        );
    }
}
