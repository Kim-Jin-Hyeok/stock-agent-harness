package com.stock.broker.order.persistence;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderReference;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmission;
import com.stock.broker.order.cancellation.BrokerOrderCancellationSubmissionStatus;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerOrderEntityTest {

    @Test
    void convertsRecordToEntityAndBack() {
        BrokerOrderRecord record = partiallyFilledOrder();

        BrokerOrderRecord restored = BrokerOrderEntity.from(record).toRecord();

        assertThat(restored).isEqualTo(record);
    }

    @Test
    void convertsPortfolioAppliedQuantityToEntityAndBack() {
        BrokerOrderRecord applied = partiallyFilledOrder()
                .markCurrentFillAppliedToPortfolio();

        BrokerOrderRecord restored = BrokerOrderEntity.from(applied)
                .toRecord();

        assertThat(restored).isEqualTo(applied);
        assertThat(restored.portfolioAppliedQuantity()).isEqualTo(3L);
        assertThat(restored.unappliedFilledQuantity()).isZero();
    }

    @Test
    void convertsAcceptedCancellationSubmissionToEntityAndBack() {
        BrokerOrderRecord record = partiallyFilledOrder();
        BrokerOrderCancellationSubmission cancellation =
                new BrokerOrderCancellationSubmission(
                        BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                        new BrokerOrderReference("0000123457", "06010"),
                        record.submittedAt().plusSeconds(20),
                        null
                );
        BrokerOrderRecord canceled = record.recordCancellation(cancellation);

        BrokerOrderRecord restored = BrokerOrderEntity.from(canceled)
                .toRecord();

        assertThat(restored).isEqualTo(canceled);
    }

    @Test
    void convertsRejectedCancellationSubmissionToEntityAndBack() {
        BrokerOrderRecord record = partiallyFilledOrder();
        BrokerOrderCancellationSubmission cancellation =
                new BrokerOrderCancellationSubmission(
                        BrokerOrderCancellationSubmissionStatus.REJECTED,
                        null,
                        record.submittedAt().plusSeconds(20),
                        "The order cannot be canceled."
                );
        BrokerOrderRecord canceled = record.recordCancellation(cancellation);

        BrokerOrderRecord restored = BrokerOrderEntity.from(canceled)
                .toRecord();

        assertThat(restored).isEqualTo(canceled);
    }

    private BrokerOrderRecord partiallyFilledOrder() {
        Instant submittedAt = Instant.parse("2026-09-23T00:00:00Z");
        return new BrokerOrderRecord(
                null,
                new BrokerOrderReference("0000123456", "06010"),
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
                3L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null,
                submittedAt,
                submittedAt.plusSeconds(300),
                submittedAt.plusSeconds(10)
        );
    }
}
