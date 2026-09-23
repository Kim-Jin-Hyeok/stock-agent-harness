package com.stock.broker.order;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerOrderRecordTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-09-23T00:00:00Z");
    private static final Instant EXPIRES_AT = SUBMITTED_AT.plusSeconds(300);

    @Test
    void createsPendingOrder() {
        BrokerOrderRecord order = pendingOrder();

        assertThat(order.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(order.cumulativeFilledQuantity()).isZero();
        assertThat(order.averageFilledPriceKrw()).isNull();
        assertThat(order.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void createsRejectedOrderWithoutBrokerOrderIdAndExpiration() {
        BrokerOrderRecord order = order(
                null,
                0L,
                null,
                BrokerOrderStatus.REJECTED,
                "Broker rejected the order.",
                null
        );

        assertThat(order.brokerOrderId()).isNull();
        assertThat(order.status()).isEqualTo(BrokerOrderStatus.REJECTED);
        assertThat(order.expiresAt()).isNull();
    }

    @Test
    void rejectsFilledQuantityGreaterThanRequestedQuantity() {
        assertThatThrownBy(() -> order(
                "0000123456",
                11L,
                70_000L,
                BrokerOrderStatus.FILLED,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "cumulativeFilledQuantity must be between 0 and requestedQuantity."
                );
    }

    @Test
    void rejectsExpirationNotAfterSubmission() {
        assertThatThrownBy(() -> order(
                "0000123456",
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null,
                SUBMITTED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after submittedAt.");
    }

    @Test
    void rejectsMissingExpirationForSubmittedOrder() {
        assertThatNullPointerException()
                .isThrownBy(() -> order(
                        "0000123456",
                        0L,
                        null,
                        BrokerOrderStatus.PENDING,
                        null,
                        null
                ))
                .withMessage("expiresAt must not be null for a submitted order.");
    }

    @Test
    void rejectsStatusThatDoesNotMatchFilledQuantity() {
        assertThatThrownBy(() -> order(
                "0000123456",
                5L,
                69_900L,
                BrokerOrderStatus.PENDING,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PENDING order must not have a filled quantity.");

        assertThatThrownBy(() -> order(
                "0000123456",
                9L,
                69_900L,
                BrokerOrderStatus.FILLED,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FILLED order must have the requested quantity filled.");
    }

    @Test
    void rejectsFilledOrderWithoutAverageFilledPrice() {
        assertThatThrownBy(() -> order(
                "0000123456",
                5L,
                null,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null,
                EXPIRES_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "averageFilledPriceKrw must be positive when an order is filled."
                );
    }

    private BrokerOrderRecord pendingOrder() {
        return order(
                "0000123456",
                0L,
                null,
                BrokerOrderStatus.PENDING,
                null,
                EXPIRES_AT
        );
    }

    private BrokerOrderRecord order(
            String brokerOrderId,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            String reason,
            Instant expiresAt
    ) {
        return new BrokerOrderRecord(
                null,
                brokerOrderId,
                "run-1",
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                ),
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L,
                cumulativeFilledQuantity,
                averageFilledPriceKrw,
                status,
                reason,
                SUBMITTED_AT,
                expiresAt,
                null
        );
    }
}
