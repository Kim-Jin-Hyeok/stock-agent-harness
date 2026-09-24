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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BrokerOrderRepositoryTest {

    @Autowired
    private BrokerOrderRepository repository;

    @Test
    void savesAndRestoresBrokerOrder() {
        BrokerOrderEntity saved = repository.saveAndFlush(
                BrokerOrderEntity.from(pendingOrder("run-1", submittedAt()))
        );

        BrokerOrderRecord restored = repository.findById(saved.getId())
                .orElseThrow()
                .toRecord();

        assertThat(restored.id()).isEqualTo(saved.getId());
        assertThat(restored.reference()).isEqualTo(
                new BrokerOrderReference("order-run-1", "06010")
        );
        assertThat(restored.runId()).isEqualTo("run-1");
        assertThat(restored.strategyIdentity()).isEqualTo(strategyIdentity());
        assertThat(restored.status()).isEqualTo(BrokerOrderStatus.PENDING);
        assertThat(restored.expiresAt()).isEqualTo(submittedAt().plusSeconds(300));
    }

    @Test
    void findsReconciliationTargetsOldestFirst() {
        Instant submittedAt = submittedAt();
        repository.save(BrokerOrderEntity.from(
                pendingOrder("run-later", submittedAt.plusSeconds(20))
        ));
        repository.save(BrokerOrderEntity.from(
                partiallyFilledOrder("run-earlier", submittedAt.plusSeconds(10))
        ));
        repository.save(BrokerOrderEntity.from(
                filledOrder("run-completed", submittedAt.plusSeconds(30))
        ));

        List<BrokerOrderEntity> targets =
                repository.findAllByStatusInOrderBySubmittedAtAsc(
                        List.of(
                                BrokerOrderStatus.PENDING,
                                BrokerOrderStatus.PARTIALLY_FILLED
                        )
                );

        assertThat(targets)
                .extracting(BrokerOrderEntity::getRunId)
                .containsExactly("run-earlier", "run-later");
    }

    @Test
    void updatesExistingOrderWithReconciledExecutionState() {
        BrokerOrderEntity saved = repository.saveAndFlush(
                BrokerOrderEntity.from(pendingOrder("run-1", submittedAt()))
        );
        Instant reconciledAt = submittedAt().plusSeconds(30);
        BrokerOrderRecord pending = saved.toRecord();
        BrokerOrderRecord reconciled = new BrokerOrderRecord(
                pending.id(),
                pending.reference(),
                pending.runId(),
                pending.strategyIdentity(),
                pending.side(),
                pending.symbol(),
                pending.requestedQuantity(),
                pending.limitPriceKrw(),
                3L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                null,
                pending.submittedAt(),
                pending.expiresAt(),
                reconciledAt
        );

        repository.saveAndFlush(BrokerOrderEntity.from(reconciled));

        BrokerOrderRecord restored = repository.findById(saved.getId())
                .orElseThrow()
                .toRecord();
        assertThat(restored.status())
                .isEqualTo(BrokerOrderStatus.PARTIALLY_FILLED);
        assertThat(restored.cumulativeFilledQuantity()).isEqualTo(3L);
        assertThat(restored.averageFilledPriceKrw()).isEqualTo(69_900L);
        assertThat(restored.lastReconciledAt()).isEqualTo(reconciledAt);
        assertThat(repository.count()).isEqualTo(1L);
    }

    @Test
    void savesAndRestoresPortfolioAppliedQuantity() {
        BrokerOrderRecord applied = partiallyFilledOrder(
                "run-1",
                submittedAt()
        ).markCurrentFillAppliedToPortfolio();

        BrokerOrderEntity saved = repository.saveAndFlush(
                BrokerOrderEntity.from(applied)
        );

        BrokerOrderRecord restored = repository.findById(saved.getId())
                .orElseThrow()
                .toRecord();
        assertThat(restored.portfolioAppliedQuantity()).isEqualTo(3L);
        assertThat(restored.unappliedFilledQuantity()).isZero();
    }

    @Test
    void savesAndRestoresCancellationSubmission() {
        BrokerOrderRecord order = pendingOrder("run-1", submittedAt());
        BrokerOrderCancellationSubmission cancellation =
                new BrokerOrderCancellationSubmission(
                        BrokerOrderCancellationSubmissionStatus.ACCEPTED,
                        new BrokerOrderReference(
                                "cancellation-order-1",
                                "06010"
                        ),
                        submittedAt().plusSeconds(30),
                        null
                );
        BrokerOrderRecord canceled = order.recordCancellation(cancellation);

        BrokerOrderEntity saved = repository.saveAndFlush(
                BrokerOrderEntity.from(canceled)
        );

        BrokerOrderRecord restored = repository.findById(saved.getId())
                .orElseThrow()
                .toRecord();
        assertThat(restored.cancellationSubmission()).isEqualTo(cancellation);
        assertThat(restored.status()).isEqualTo(BrokerOrderStatus.PENDING);
    }

    @Test
    void findsExpiredOrdersWithoutCancellationOldestFirst() {
        Instant submittedAt = submittedAt();
        Instant now = submittedAt.plusSeconds(360);
        repository.save(BrokerOrderEntity.from(
                pendingOrder("run-earlier", submittedAt)
        ));
        repository.save(BrokerOrderEntity.from(
                partiallyFilledOrder(
                        "run-boundary",
                        submittedAt.plusSeconds(60)
                )
        ));
        repository.save(BrokerOrderEntity.from(
                pendingOrder("run-future", submittedAt.plusSeconds(61))
        ));
        repository.save(BrokerOrderEntity.from(
                filledOrder("run-filled", submittedAt.plusSeconds(10))
        ));
        BrokerOrderRecord cancellationAttempted = pendingOrder(
                "run-attempted",
                submittedAt.plusSeconds(30)
        ).recordCancellation(new BrokerOrderCancellationSubmission(
                BrokerOrderCancellationSubmissionStatus.REJECTED,
                null,
                submittedAt.plusSeconds(340),
                "The order cannot be canceled."
        ));
        repository.save(BrokerOrderEntity.from(cancellationAttempted));

        List<BrokerOrderEntity> targets = repository
                .findAllByStatusInAndExpiresAtLessThanEqualAndCancellationStatusIsNullOrderByExpiresAtAsc(
                        List.of(
                                BrokerOrderStatus.PENDING,
                                BrokerOrderStatus.PARTIALLY_FILLED
                        ),
                        now
                );

        assertThat(targets)
                .extracting(BrokerOrderEntity::getRunId)
                .containsExactly("run-earlier", "run-boundary");
    }

    private BrokerOrderRecord pendingOrder(String runId, Instant submittedAt) {
        return order(
                runId,
                0L,
                null,
                BrokerOrderStatus.PENDING,
                submittedAt
        );
    }

    private BrokerOrderRecord partiallyFilledOrder(String runId, Instant submittedAt) {
        return order(
                runId,
                3L,
                69_900L,
                BrokerOrderStatus.PARTIALLY_FILLED,
                submittedAt
        );
    }

    private BrokerOrderRecord filledOrder(String runId, Instant submittedAt) {
        return order(
                runId,
                10L,
                69_800L,
                BrokerOrderStatus.FILLED,
                submittedAt
        );
    }

    private BrokerOrderRecord order(
            String runId,
            long cumulativeFilledQuantity,
            Long averageFilledPriceKrw,
            BrokerOrderStatus status,
            Instant submittedAt
    ) {
        return new BrokerOrderRecord(
                null,
                new BrokerOrderReference("order-" + runId, "06010"),
                runId,
                strategyIdentity(),
                BrokerOrderSide.BUY,
                "005930",
                10L,
                70_000L,
                cumulativeFilledQuantity,
                averageFilledPriceKrw,
                status,
                null,
                submittedAt,
                submittedAt.plusSeconds(300),
                null
        );
    }

    private InvestmentStrategyIdentity strategyIdentity() {
        return new InvestmentStrategyIdentity(
                "DAY_TRADING_V1",
                1,
                InvestmentHorizon.DAY_TRADING
        );
    }

    private Instant submittedAt() {
        return Instant.parse("2026-09-23T00:00:00Z");
    }
}
