package com.stock.strategy.indicator.movingaverage.policy;

import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.movingaverage.MovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.ConfiguredStrategyMovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.StrategyMovingAverageProperties;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyMovingAveragePeriodPolicyTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    @Test
    void returnsPeriodsForStrategyIdentity() {
        StrategyMovingAveragePeriodPolicy policy = policy(5, 20, 21);

        MovingAveragePeriods periods = policy.getPeriods(
                STRATEGY_IDENTITY
        );

        assertThat(periods).isEqualTo(new MovingAveragePeriods(5, 20));
    }

    @Test
    void rejectsUnregisteredStrategyIdentity() {
        StrategyMovingAveragePeriodPolicy policy = policy(5, 20, 21);
        InvestmentStrategyIdentity unknown = new InvestmentStrategyIdentity(
                "DAY_TRADING_V2",
                2,
                InvestmentHorizon.DAY_TRADING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.getPeriods(unknown))
                .withMessage(
                        "Strategy moving average periods not found: "
                                + unknown
                );
    }

    @Test
    void rejectsHistoryLimitWithoutPreviousIndicatorBar() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy(5, 20, 20))
                .withMessageContaining(
                        "Moving average crossover analysis requires more "
                                + "daily price bars than configured."
                )
                .withMessageContaining("longPeriod=20")
                .withMessageContaining("requiredBarCount=21")
                .withMessageContaining("latestBarCount=20")
                .withMessageContaining(STRATEGY_IDENTITY.toString());
    }

    private StrategyMovingAveragePeriodPolicy policy(
            int shortPeriod,
            int longPeriod,
            int latestBarCount
    ) {
        StrategyMovingAverageProperties movingAverageProperties =
                new StrategyMovingAverageProperties(List.of(
                        new ConfiguredStrategyMovingAveragePeriods(
                                STRATEGY_IDENTITY.strategyId(),
                                STRATEGY_IDENTITY.strategyVersion(),
                                STRATEGY_IDENTITY.horizon(),
                                shortPeriod,
                                longPeriod
                        )
                ));
        StrategyDailyPriceHistoryPolicy historyPolicy =
                new StrategyDailyPriceHistoryPolicy(
                        new StrategyDailyPriceHistoryProperties(List.of(
                                new ConfiguredStrategyDailyPriceHistoryLimit(
                                        STRATEGY_IDENTITY.strategyId(),
                                        STRATEGY_IDENTITY.strategyVersion(),
                                        STRATEGY_IDENTITY.horizon(),
                                        latestBarCount
                                )
                        ))
                );
        return new StrategyMovingAveragePeriodPolicy(
                movingAverageProperties,
                historyPolicy
        );
    }
}
