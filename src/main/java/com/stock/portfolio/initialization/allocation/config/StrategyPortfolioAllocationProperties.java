package com.stock.portfolio.initialization.allocation.config;

import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "portfolio.initialization")
public record StrategyPortfolioAllocationProperties(
        List<StrategyAllocationProperties> allocations
) {
    public StrategyPortfolioAllocationProperties {
        allocations = List.copyOf(Objects.requireNonNull(
                allocations,
                "Strategy portfolio allocations must not be null."
        ));
        if (allocations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Strategy portfolio allocations must not be empty."
            );
        }

        Set<InvestmentStrategyIdentity> identities = new HashSet<>();
        long totalWeight = 0L;
        for (StrategyAllocationProperties allocation : allocations) {
            Objects.requireNonNull(
                    allocation,
                    "Strategy portfolio allocation must not be null."
            );
            InvestmentStrategyIdentity identity = allocation.strategyIdentity();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate strategy portfolio allocation: " + identity
                );
            }
            try {
                totalWeight = Math.addExact(totalWeight, allocation.weight());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Strategy portfolio allocation weight is too large.",
                        exception
                );
            }
        }
    }
}
