package com.stock.strategy.indicator.volatility.atr.policy;

import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.volatility.atr.config.ConfiguredStrategyAverageTrueRangePeriod;
import com.stock.strategy.indicator.volatility.atr.config.StrategyAverageTrueRangeProperties;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyAverageTrueRangePeriodPolicyTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    @Test
    void returnsPeriodForStrategyIdentity() {
        StrategyAverageTrueRangePeriodPolicy policy = policy(14, 120);

        int period = policy.getPeriod(STRATEGY_IDENTITY);

        assertThat(period).isEqualTo(14);
    }

    @Test
    void rejectsUnregisteredStrategyIdentity() {
        StrategyAverageTrueRangePeriodPolicy policy = policy(14, 120);
        InvestmentStrategyIdentity unknown = new InvestmentStrategyIdentity(
                "SWING_V2",
                2,
                InvestmentHorizon.SWING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.getPeriod(unknown))
                .withMessage(
                        "Strategy average true range period not found: "
                                + unknown
                );
    }

    @Test
    void rejectsHistoryLimitWithoutPreviousPriceBar() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy(14, 14))
                .withMessageContaining(
                        "Average true range analysis requires more daily "
                                + "price bars than configured."
                )
                .withMessageContaining("period=14")
                .withMessageContaining("requiredBarCount=15")
                .withMessageContaining("latestBarCount=14")
                .withMessageContaining(STRATEGY_IDENTITY.toString());
    }

    private StrategyAverageTrueRangePeriodPolicy policy(
            int period,
            int latestBarCount
    ) {
        StrategyAverageTrueRangeProperties averageTrueRangeProperties =
                new StrategyAverageTrueRangeProperties(List.of(
                        new ConfiguredStrategyAverageTrueRangePeriod(
                                STRATEGY_IDENTITY.strategyId(),
                                STRATEGY_IDENTITY.strategyVersion(),
                                STRATEGY_IDENTITY.horizon(),
                                period
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
        return new StrategyAverageTrueRangePeriodPolicy(
                averageTrueRangeProperties,
                historyPolicy
        );
    }
}
