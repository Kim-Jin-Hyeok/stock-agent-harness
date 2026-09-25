package com.stock.strategy.data.history;

import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StrategyDailyPriceHistoryPolicyTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final StrategyDailyPriceHistoryPolicy policy =
            new StrategyDailyPriceHistoryPolicy(
                    new StrategyDailyPriceHistoryProperties(List.of(
                            new ConfiguredStrategyDailyPriceHistoryLimit(
                                    STRATEGY_IDENTITY.strategyId(),
                                    STRATEGY_IDENTITY.strategyVersion(),
                                    STRATEGY_IDENTITY.horizon(),
                                    60
                            )
                    ))
            );

    @Test
    void returnsLatestBarCountForStrategyIdentity() {
        int latestBarCount = policy.getLatestBarCount(STRATEGY_IDENTITY);

        assertThat(latestBarCount).isEqualTo(60);
    }

    @Test
    void rejectsUnregisteredStrategyIdentity() {
        InvestmentStrategyIdentity unknown = new InvestmentStrategyIdentity(
                "DAY_TRADING_V2",
                2,
                InvestmentHorizon.DAY_TRADING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> policy.getLatestBarCount(unknown))
                .withMessage(
                        "Strategy daily price history limit not found: "
                        + unknown
                );
    }
}
