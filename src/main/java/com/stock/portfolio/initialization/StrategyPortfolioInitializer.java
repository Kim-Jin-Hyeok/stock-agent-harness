package com.stock.portfolio.initialization;

import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class StrategyPortfolioInitializer {

    public PortfolioSnapshot initialize(InvestmentStrategyIdentity strategyIdentity) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");

        return new PortfolioSnapshot(
                10_000_000L,
                10_000_000L,
                List.of()
        );
    }
}
