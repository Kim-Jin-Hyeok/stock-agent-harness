package com.stock.broker.order.persistence;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
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
        assertThat(restored.brokerOrderId()).isEqualTo("order-run-1");
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
                "order-" + runId,
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
