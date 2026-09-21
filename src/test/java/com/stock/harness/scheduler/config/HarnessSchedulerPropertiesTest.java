package com.stock.harness.scheduler.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessSchedulerPropertiesTest {

    @Test
    void copiesConfiguredStrategyList() {
        List<ScheduledStrategyProperties> source = new ArrayList<>(List.of(
                scheduledStrategy("DAY_TRADING_V1", InvestmentHorizon.DAY_TRADING)
        ));
        HarnessSchedulerProperties properties = new HarnessSchedulerProperties(
                true,
                "Asia/Seoul",
                source
        );

        source.clear();

        assertThat(properties.strategies()).hasSize(1);
        assertThatThrownBy(() -> properties.strategies().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void convertsConfiguredStrategiesToInvestmentStrategyIdentities() {
        HarnessSchedulerProperties properties = new HarnessSchedulerProperties(
                true,
                "Asia/Seoul",
                List.of(
                        scheduledStrategy("DAY_TRADING_V1", InvestmentHorizon.DAY_TRADING),
                        scheduledStrategy("SWING_V1", InvestmentHorizon.SWING),
                        scheduledStrategy("LONG_TERM_V1", InvestmentHorizon.LONG_TERM)
                )
        );

        List<InvestmentStrategyIdentity> identities = properties.strategies().stream()
                .map(ScheduledStrategyProperties::strategyIdentity)
                .toList();

        assertThat(identities).containsExactly(
                new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING),
                new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING),
                new InvestmentStrategyIdentity("LONG_TERM_V1", 1, InvestmentHorizon.LONG_TERM)
        );
    }

    private ScheduledStrategyProperties scheduledStrategy(
            String strategyId,
            InvestmentHorizon horizon
    ) {
        return new ScheduledStrategyProperties(
                true,
                strategyId,
                1,
                horizon,
                new StrategyRunWindowProperties(
                        Set.of(DayOfWeek.MONDAY),
                        LocalTime.of(9, 0),
                        LocalTime.of(15, 30)
                )
        );
    }
}
