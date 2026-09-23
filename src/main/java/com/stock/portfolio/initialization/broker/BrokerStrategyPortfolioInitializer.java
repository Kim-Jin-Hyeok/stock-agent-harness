package com.stock.portfolio.initialization.broker;

import com.stock.broker.account.BrokerAccountSnapshot;
import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.initialization.StrategyPortfolioInitializer;
import com.stock.portfolio.initialization.allocation.StrategyPortfolioAllocationPolicy;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "broker.kis",
        name = "enabled",
        havingValue = "true"
)
public class BrokerStrategyPortfolioInitializer
        implements StrategyPortfolioInitializer {
    private final BrokerAccountProvider brokerAccountProvider;
    private final StrategyPortfolioAllocationPolicy allocationPolicy;

    public BrokerStrategyPortfolioInitializer(
            BrokerAccountProvider brokerAccountProvider,
            StrategyPortfolioAllocationPolicy allocationPolicy
    ) {
        this.brokerAccountProvider = Objects.requireNonNull(
                brokerAccountProvider,
                "brokerAccountProvider must not be null."
        );
        this.allocationPolicy = Objects.requireNonNull(
                allocationPolicy,
                "allocationPolicy must not be null."
        );
    }

    @Override
    public PortfolioSnapshot initialize(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        BrokerAccountSnapshot brokerSnapshot =
                brokerAccountProvider.getAccountSnapshot();

        return allocationPolicy.allocate(brokerSnapshot, strategyIdentity);
    }
}
