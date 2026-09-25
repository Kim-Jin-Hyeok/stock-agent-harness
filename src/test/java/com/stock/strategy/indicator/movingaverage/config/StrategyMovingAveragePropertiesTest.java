package com.stock.strategy.indicator.movingaverage.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyMovingAveragePropertiesTest {

    @Test
    void copiesConfiguredPeriods() {
        List<ConfiguredStrategyMovingAveragePeriods> periods =
                new ArrayList<>(List.of(periods(5, 20)));

        StrategyMovingAverageProperties properties =
                new StrategyMovingAverageProperties(periods);
        periods.add(periods(
                "SWING_V1",
                InvestmentHorizon.SWING,
                20,
                60
        ));

        assertThat(properties.periods()).hasSize(1);
    }

    @Test
    void rejectsEmptyPeriods() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyMovingAverageProperties(
                        List.of()
                ))
                .withMessage(
                        "Strategy moving average periods must not be empty."
                );
    }

    @Test
    void rejectsDuplicateStrategyIdentity() {
        ConfiguredStrategyMovingAveragePeriods configured = periods(5, 20);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyMovingAverageProperties(
                        List.of(configured, configured)
                ))
                .withMessageStartingWith(
                        "Duplicate strategy moving average periods:"
                );
    }

    private ConfiguredStrategyMovingAveragePeriods periods(
            int shortPeriod,
            int longPeriod
    ) {
        return periods(
                "DAY_TRADING_V1",
                InvestmentHorizon.DAY_TRADING,
                shortPeriod,
                longPeriod
        );
    }

    private ConfiguredStrategyMovingAveragePeriods periods(
            String strategyId,
            InvestmentHorizon horizon,
            int shortPeriod,
            int longPeriod
    ) {
        return new ConfiguredStrategyMovingAveragePeriods(
                strategyId,
                1,
                horizon,
                shortPeriod,
                longPeriod
        );
    }
}
