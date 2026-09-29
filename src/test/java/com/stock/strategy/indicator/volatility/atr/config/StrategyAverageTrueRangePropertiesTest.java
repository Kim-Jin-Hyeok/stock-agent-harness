package com.stock.strategy.indicator.volatility.atr.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyAverageTrueRangePropertiesTest {

    @Test
    void copiesConfiguredPeriods() {
        List<ConfiguredStrategyAverageTrueRangePeriod> periods =
                new ArrayList<>(List.of(period(14)));

        StrategyAverageTrueRangeProperties properties =
                new StrategyAverageTrueRangeProperties(periods);
        periods.add(period(
                "LONG_TERM_V1",
                InvestmentHorizon.LONG_TERM,
                20
        ));

        assertThat(properties.periods()).hasSize(1);
    }

    @Test
    void rejectsEmptyPeriods() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyAverageTrueRangeProperties(
                        List.of()
                ))
                .withMessage(
                        "Strategy average true range periods must not be "
                                + "empty."
                );
    }

    @Test
    void rejectsDuplicateStrategyIdentity() {
        ConfiguredStrategyAverageTrueRangePeriod configured = period(14);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyAverageTrueRangeProperties(
                        List.of(configured, configured)
                ))
                .withMessageStartingWith(
                        "Duplicate strategy average true range period:"
                );
    }

    @Test
    void rejectsNonPositivePeriod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> period(0))
                .withMessage("period must be at least 1.");
    }

    private ConfiguredStrategyAverageTrueRangePeriod period(int period) {
        return period(
                "SWING_V1",
                InvestmentHorizon.SWING,
                period
        );
    }

    private ConfiguredStrategyAverageTrueRangePeriod period(
            String strategyId,
            InvestmentHorizon horizon,
            int period
    ) {
        return new ConfiguredStrategyAverageTrueRangePeriod(
                strategyId,
                1,
                horizon,
                period
        );
    }
}
