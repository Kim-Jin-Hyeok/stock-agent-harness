package com.stock.trade.execution.config;

import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.portfolio.PortfolioService;
import com.stock.trade.execution.TradeExecutionHandler;
import com.stock.trade.execution.broker.BrokerTradeExecutionHandler;
import com.stock.trade.execution.virtual.VirtualTradeExecutionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TradeExecutionConfigurationTest {

    @Test
    void createsVirtualHandlerForVirtualMode() {
        contextRunner(TradeExecutionMode.VIRTUAL)
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            TradeExecutionHandler.class
                    );
                    assertThat(context.getBean(TradeExecutionHandler.class))
                            .isInstanceOf(VirtualTradeExecutionHandler.class);
                });
    }

    @Test
    void createsBrokerHandlerForBrokerMode() {
        contextRunner(TradeExecutionMode.BROKER)
                .withBean(
                        BrokerOrderSubmissionService.class,
                        () -> mock(BrokerOrderSubmissionService.class)
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            TradeExecutionHandler.class
                    );
                    assertThat(context.getBean(TradeExecutionHandler.class))
                            .isInstanceOf(BrokerTradeExecutionHandler.class);
                });
    }

    @Test
    void brokerModeFailsWithoutBrokerOrderSubmissionService() {
        contextRunner(TradeExecutionMode.BROKER)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "BrokerOrderSubmissionService must be configured "
                                            + "when trade execution mode is BROKER."
                            );
                });
    }

    private ApplicationContextRunner contextRunner(TradeExecutionMode mode) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TradeExecutionConfiguration.class)
                .withBean(
                        TradeExecutionProperties.class,
                        () -> new TradeExecutionProperties(mode)
                )
                .withBean(
                        PortfolioService.class,
                        () -> mock(PortfolioService.class)
                );
    }
}
