package com.stock.portfolio.initialization.fixed;

import com.stock.portfolio.PortfolioSnapshot;
import com.stock.portfolio.initialization.StrategyPortfolioInitializer;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "broker.kis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class FixedStrategyPortfolioInitializer
        implements StrategyPortfolioInitializer {
    private static final long INITIAL_AMOUNT_KRW = 10_000_000L;

    @Override
    public PortfolioSnapshot initialize(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );

        return new PortfolioSnapshot(
                INITIAL_AMOUNT_KRW,
                INITIAL_AMOUNT_KRW,
                List.of()
        );
    }
}
