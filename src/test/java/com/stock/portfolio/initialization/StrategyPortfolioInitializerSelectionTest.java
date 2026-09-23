package com.stock.portfolio.initialization;

import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.portfolio.initialization.allocation.StrategyPortfolioAllocationPolicy;
import com.stock.portfolio.initialization.broker.BrokerStrategyPortfolioInitializer;
import com.stock.portfolio.initialization.fixed.FixedStrategyPortfolioInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StrategyPortfolioInitializerSelectionTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            FixedStrategyPortfolioInitializer.class,
                            BrokerStrategyPortfolioInitializer.class
                    );

    @Test
    void selectsFixedInitializerWhenKisIsDisabled() {
        contextRunner
                .withPropertyValues("broker.kis.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            StrategyPortfolioInitializer.class
                    );
                    assertThat(context.getBean(
                            StrategyPortfolioInitializer.class
                    )).isInstanceOf(FixedStrategyPortfolioInitializer.class);
                });
    }

    @Test
    void selectsBrokerInitializerWhenKisIsEnabled() {
        contextRunner
                .withPropertyValues("broker.kis.enabled=true")
                .withBean(
                        BrokerAccountProvider.class,
                        () -> mock(BrokerAccountProvider.class)
                )
                .withBean(
                        StrategyPortfolioAllocationPolicy.class,
                        () -> mock(StrategyPortfolioAllocationPolicy.class)
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            StrategyPortfolioInitializer.class
                    );
                    assertThat(context.getBean(
                            StrategyPortfolioInitializer.class
                    )).isInstanceOf(BrokerStrategyPortfolioInitializer.class);
                });
    }
}
