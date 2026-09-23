package com.stock.portfolio.initialization.broker;

import com.stock.broker.account.BrokerAccountSnapshot;
import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.initialization.allocation.StrategyPortfolioAllocationPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BrokerStrategyPortfolioInitializerTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    @Test
    void allocatesBrokerAccountSnapshotForStrategy() {
        BrokerAccountProvider brokerAccountProvider = mock(
                BrokerAccountProvider.class
        );
        StrategyPortfolioAllocationPolicy allocationPolicy = mock(
                StrategyPortfolioAllocationPolicy.class
        );
        BrokerAccountSnapshot brokerSnapshot = new BrokerAccountSnapshot(
                10_000_000L,
                10_000_000L,
                List.of(),
                Instant.parse("2026-09-23T00:00:00Z")
        );
        PortfolioSnapshot expected = new PortfolioSnapshot(
                3_333_334L,
                3_333_334L,
                List.of()
        );
        when(brokerAccountProvider.getAccountSnapshot())
                .thenReturn(brokerSnapshot);
        when(allocationPolicy.allocate(brokerSnapshot, STRATEGY_IDENTITY))
                .thenReturn(expected);
        BrokerStrategyPortfolioInitializer initializer =
                new BrokerStrategyPortfolioInitializer(
                        brokerAccountProvider,
                        allocationPolicy
                );

        PortfolioSnapshot result = initializer.initialize(STRATEGY_IDENTITY);

        assertThat(result).isSameAs(expected);
        var inOrder = inOrder(brokerAccountProvider, allocationPolicy);
        inOrder.verify(brokerAccountProvider).getAccountSnapshot();
        inOrder.verify(allocationPolicy).allocate(
                brokerSnapshot,
                STRATEGY_IDENTITY
        );
    }

    @Test
    void rejectsNullStrategyBeforeBrokerCall() {
        BrokerAccountProvider brokerAccountProvider = mock(
                BrokerAccountProvider.class
        );
        StrategyPortfolioAllocationPolicy allocationPolicy = mock(
                StrategyPortfolioAllocationPolicy.class
        );
        BrokerStrategyPortfolioInitializer initializer =
                new BrokerStrategyPortfolioInitializer(
                        brokerAccountProvider,
                        allocationPolicy
                );

        assertThatNullPointerException()
                .isThrownBy(() -> initializer.initialize(null))
                .withMessage("strategyIdentity must not be null.");
        verifyNoInteractions(brokerAccountProvider, allocationPolicy);
    }

    @Test
    void propagatesBrokerAccountFailureWithoutAllocation() {
        BrokerAccountProvider brokerAccountProvider = mock(
                BrokerAccountProvider.class
        );
        StrategyPortfolioAllocationPolicy allocationPolicy = mock(
                StrategyPortfolioAllocationPolicy.class
        );
        RuntimeException failure = new RuntimeException("broker failed");
        when(brokerAccountProvider.getAccountSnapshot()).thenThrow(failure);
        BrokerStrategyPortfolioInitializer initializer =
                new BrokerStrategyPortfolioInitializer(
                        brokerAccountProvider,
                        allocationPolicy
                );

        assertThatThrownBy(() -> initializer.initialize(STRATEGY_IDENTITY))
                .isSameAs(failure);
        verifyNoInteractions(allocationPolicy);
    }

    @Test
    void propagatesAllocationFailure() {
        BrokerAccountProvider brokerAccountProvider = mock(
                BrokerAccountProvider.class
        );
        StrategyPortfolioAllocationPolicy allocationPolicy = mock(
                StrategyPortfolioAllocationPolicy.class
        );
        BrokerAccountSnapshot brokerSnapshot = new BrokerAccountSnapshot(
                10_000_000L,
                10_000_000L,
                List.of(),
                Instant.parse("2026-09-23T00:00:00Z")
        );
        RuntimeException failure = new RuntimeException("allocation failed");
        when(brokerAccountProvider.getAccountSnapshot())
                .thenReturn(brokerSnapshot);
        when(allocationPolicy.allocate(brokerSnapshot, STRATEGY_IDENTITY))
                .thenThrow(failure);
        BrokerStrategyPortfolioInitializer initializer =
                new BrokerStrategyPortfolioInitializer(
                        brokerAccountProvider,
                        allocationPolicy
                );

        assertThatThrownBy(() -> initializer.initialize(STRATEGY_IDENTITY))
                .isSameAs(failure);
        verify(brokerAccountProvider).getAccountSnapshot();
    }
}
