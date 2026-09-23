package com.stock.portfolio.initialization.allocation;

import com.stock.broker.account.BrokerAccountPosition;
import com.stock.broker.account.BrokerAccountSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.initialization.allocation.config.StrategyAllocationProperties;
import com.stock.portfolio.initialization.allocation.config.StrategyPortfolioAllocationProperties;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class StrategyPortfolioAllocationPolicyTest {
    private static final InvestmentStrategyIdentity DAY_TRADING = identity(
            "DAY_TRADING_V1",
            InvestmentHorizon.DAY_TRADING
    );
    private static final InvestmentStrategyIdentity SWING = identity(
            "SWING_V1",
            InvestmentHorizon.SWING
    );
    private static final InvestmentStrategyIdentity LONG_TERM = identity(
            "LONG_TERM_V1",
            InvestmentHorizon.LONG_TERM
    );
    private static final Instant OBSERVED_AT =
            Instant.parse("2026-09-23T00:00:00Z");

    @Test
    void allocatesEqualWeightsAndDistributesRemainderInConfigurationOrder() {
        StrategyPortfolioAllocationPolicy policy = policy(1L, 1L, 1L);
        BrokerAccountSnapshot brokerSnapshot = brokerSnapshot(
                10_000_000L,
                10_000_001L,
                List.of()
        );

        PortfolioSnapshot dayTrading = policy.allocate(
                brokerSnapshot,
                DAY_TRADING
        );
        PortfolioSnapshot swing = policy.allocate(brokerSnapshot, SWING);
        PortfolioSnapshot longTerm = policy.allocate(
                brokerSnapshot,
                LONG_TERM
        );

        assertThat(dayTrading.cashAmountKrw()).isEqualTo(3_333_334L);
        assertThat(swing.cashAmountKrw()).isEqualTo(3_333_333L);
        assertThat(longTerm.cashAmountKrw()).isEqualTo(3_333_333L);
        assertThat(dayTrading.totalAssetAmountKrw()).isEqualTo(3_333_334L);
        assertThat(swing.totalAssetAmountKrw()).isEqualTo(3_333_334L);
        assertThat(longTerm.totalAssetAmountKrw()).isEqualTo(3_333_333L);
        assertThat(dayTrading.positions()).isEmpty();
    }

    @Test
    void allocatesAmountsByConfiguredWeights() {
        StrategyPortfolioAllocationPolicy policy = policy(2L, 1L, 1L);
        BrokerAccountSnapshot brokerSnapshot = brokerSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );

        assertThat(policy.allocate(brokerSnapshot, DAY_TRADING).cashAmountKrw())
                .isEqualTo(5_000_000L);
        assertThat(policy.allocate(brokerSnapshot, SWING).cashAmountKrw())
                .isEqualTo(2_500_000L);
        assertThat(policy.allocate(brokerSnapshot, LONG_TERM).cashAmountKrw())
                .isEqualTo(2_500_000L);
    }

    @Test
    void rejectsStrategyWithoutAllocation() {
        StrategyPortfolioAllocationPolicy policy = policy(1L, 1L, 1L);
        InvestmentStrategyIdentity unknown = identity(
                "UNKNOWN_V1",
                InvestmentHorizon.SWING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.allocate(
                        brokerSnapshot(10_000_000L, 10_000_000L, List.of()),
                        unknown
                ))
                .withMessage(
                        "Strategy portfolio allocation was not configured: "
                                + unknown
                );
    }

    @Test
    void rejectsBrokerAccountWithExistingPositions() {
        StrategyPortfolioAllocationPolicy policy = policy(1L, 1L, 1L);
        BrokerAccountPosition position = new BrokerAccountPosition(
                "005930",
                1L,
                70_000L,
                72_000L
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> policy.allocate(
                        brokerSnapshot(9_928_000L, 10_000_000L, List.of(position)),
                        DAY_TRADING
                ))
                .withMessage(
                        "Broker account must not have positions before strategy "
                                + "portfolio initialization."
                );
    }

    private StrategyPortfolioAllocationPolicy policy(
            long dayTradingWeight,
            long swingWeight,
            long longTermWeight
    ) {
        return new StrategyPortfolioAllocationPolicy(
                new StrategyPortfolioAllocationProperties(List.of(
                        allocation(DAY_TRADING, dayTradingWeight),
                        allocation(SWING, swingWeight),
                        allocation(LONG_TERM, longTermWeight)
                ))
        );
    }

    private StrategyAllocationProperties allocation(
            InvestmentStrategyIdentity identity,
            long weight
    ) {
        return new StrategyAllocationProperties(
                identity.strategyId(),
                identity.strategyVersion(),
                identity.horizon(),
                weight
        );
    }

    private BrokerAccountSnapshot brokerSnapshot(
            long depositAmountKrw,
            long totalAssetAmountKrw,
            List<BrokerAccountPosition> positions
    ) {
        return new BrokerAccountSnapshot(
                depositAmountKrw,
                totalAssetAmountKrw,
                positions,
                OBSERVED_AT
        );
    }

    private static InvestmentStrategyIdentity identity(
            String strategyId,
            InvestmentHorizon horizon
    ) {
        return new InvestmentStrategyIdentity(strategyId, 1, horizon);
    }
}
