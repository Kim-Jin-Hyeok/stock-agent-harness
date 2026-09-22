package com.stock.broker.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerAccountPositionTest {

    @Test
    void createsPositionWithValidValues() {
        BrokerAccountPosition position = new BrokerAccountPosition(
                "005930",
                10L,
                70_000L,
                700_000L
        );

        assertThat(position.symbol()).isEqualTo("005930");
        assertThat(position.quantity()).isEqualTo(10L);
        assertThat(position.averagePriceKrw()).isEqualTo(70_000L);
        assertThat(position.evaluationAmountKrw()).isEqualTo(700_000L);
    }

    @Test
    void rejectsBlankSymbol() {
        assertThatThrownBy(() -> new BrokerAccountPosition(" ", 0L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must not be blank.");
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> new BrokerAccountPosition("005930", -1L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must not be negative.");
        assertThatThrownBy(() -> new BrokerAccountPosition("005930", 0L, -1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("averagePriceKrw must not be negative.");
        assertThatThrownBy(() -> new BrokerAccountPosition("005930", 0L, 0L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("evaluationAmountKrw must not be negative.");
    }
}
