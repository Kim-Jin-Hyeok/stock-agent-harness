package com.stock.broker.order.persistence;

import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
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

    private BrokerOrderRecord partiallyFilledOrder() {
        Instant submittedAt = Instant.parse("2026-09-23T00:00:00Z");
        return new BrokerOrderRecord(
                null,
                "0000123456",
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
