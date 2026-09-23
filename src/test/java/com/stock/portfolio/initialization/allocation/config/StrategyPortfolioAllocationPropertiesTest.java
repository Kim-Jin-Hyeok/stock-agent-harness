package com.stock.portfolio.initialization.allocation.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyPortfolioAllocationPropertiesTest {

    @Test
    void rejectsNonPositiveWeight() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> allocation("DAY_TRADING_V1", 0L))
                .withMessage("Strategy allocation weight must be positive.");
    }

    @Test
    void rejectsDuplicateStrategyIdentity() {
        StrategyAllocationProperties allocation = allocation(
                "DAY_TRADING_V1",
                1L
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyPortfolioAllocationProperties(
                        List.of(allocation, allocation)
                ))
                .withMessageStartingWith(
                        "Duplicate strategy portfolio allocation:"
                );
    }

    private StrategyAllocationProperties allocation(
            String strategyId,
            long weight
    ) {
        return new StrategyAllocationProperties(
                strategyId,
                1,
                InvestmentHorizon.DAY_TRADING,
                weight
        );
    }
}
