package com.stock.strategy.data.history.config;

import com.stock.strategy.profile.InvestmentHorizon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyDailyPriceHistoryPropertiesTest {

    @Test
    void copiesConfiguredLimits() {
        List<ConfiguredStrategyDailyPriceHistoryLimit> limits =
                new ArrayList<>(List.of(limit(60)));

        StrategyDailyPriceHistoryProperties properties =
                new StrategyDailyPriceHistoryProperties(limits);
        limits.add(limit("SWING_V1", InvestmentHorizon.SWING, 120));

        assertThat(properties.limits()).hasSize(1);
    }

    @Test
    void rejectsNonPositiveLatestBarCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> limit(0))
                .withMessage("latestBarCount must be at least 1.");
    }

    @Test
    void rejectsEmptyLimits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyDailyPriceHistoryProperties(
                        List.of()
                ))
                .withMessage(
                        "Strategy daily price history limits must not be empty."
                );
    }

    @Test
    void rejectsDuplicateStrategyIdentity() {
        ConfiguredStrategyDailyPriceHistoryLimit limit = limit(60);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StrategyDailyPriceHistoryProperties(
                        List.of(limit, limit)
                ))
                .withMessageStartingWith(
                        "Duplicate strategy daily price history limit:"
                );
    }

    private ConfiguredStrategyDailyPriceHistoryLimit limit(
            int latestBarCount
    ) {
        return limit(
                "DAY_TRADING_V1",
                InvestmentHorizon.DAY_TRADING,
                latestBarCount
        );
    }

    private ConfiguredStrategyDailyPriceHistoryLimit limit(
            String strategyId,
            InvestmentHorizon horizon,
            int latestBarCount
    ) {
        return new ConfiguredStrategyDailyPriceHistoryLimit(
                strategyId,
                1,
                horizon,
                latestBarCount
        );
    }
}
