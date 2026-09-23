package com.stock.broker.order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderRequestTest {

    @Test
    void createsRequestWithValidValues() {
        BrokerOrderRequest request = new BrokerOrderRequest(
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L
        );

        assertThat(request.side()).isEqualTo(BrokerOrderSide.BUY);
        assertThat(request.symbol()).isEqualTo("005930");
        assertThat(request.quantity()).isEqualTo(10L);
        assertThat(request.limitPriceKrw()).isEqualTo(70_000L);
    }

    @Test
    void rejectsNullSide() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerOrderRequest(
                        null,
                        "005930",
                        10L,
                        70_000L
                ))
                .withMessage("side must not be null.");
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new BrokerOrderRequest(
                BrokerOrderSide.BUY,
                " ",
                10L,
                70_000L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new BrokerOrderRequest(
                BrokerOrderSide.BUY,
                "005930",
                0L,
                70_000L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be positive.");
    }

    @Test
    void rejectsNonPositiveLimitPrice() {
        assertThatThrownBy(() -> new BrokerOrderRequest(
                BrokerOrderSide.BUY,
                "005930",
                10L,
                0L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limitPriceKrw must be positive.");
    }
}
