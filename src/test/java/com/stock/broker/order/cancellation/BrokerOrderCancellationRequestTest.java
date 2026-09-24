package com.stock.broker.order.cancellation;

import com.stock.broker.order.BrokerOrderReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BrokerOrderCancellationRequestTest {

    @Test
    void createsCancellationRequestForOriginalOrder() {
        BrokerOrderReference reference = orderReference();

        BrokerOrderCancellationRequest request =
                new BrokerOrderCancellationRequest(reference);

        assertThat(request.reference()).isEqualTo(reference);
    }

    @Test
    void rejectsNullOrderReference() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderCancellationRequest(null))
                .withMessage("reference must not be null.");
    }

    private BrokerOrderReference orderReference() {
        return new BrokerOrderReference("0000123456", "06010");
    }
}
