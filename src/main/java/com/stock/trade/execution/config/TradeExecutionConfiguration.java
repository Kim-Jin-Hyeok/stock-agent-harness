package com.stock.trade.execution.config;

import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.portfolio.PortfolioService;
import com.stock.trade.execution.TradeExecutionHandler;
import com.stock.trade.execution.broker.BrokerTradeExecutionHandler;
import com.stock.trade.execution.virtual.VirtualTradeExecutionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TradeExecutionConfiguration {

    @Bean
    public TradeExecutionHandler tradeExecutionHandler(
            TradeExecutionProperties properties,
            PortfolioService portfolioService,
            ObjectProvider<BrokerOrderSubmissionService> submissionServiceProvider
    ) {
        return switch (properties.mode()) {
            case VIRTUAL -> new VirtualTradeExecutionHandler(portfolioService);
            case BROKER -> new BrokerTradeExecutionHandler(
                    requireSubmissionService(submissionServiceProvider)
            );
        };
    }

    private BrokerOrderSubmissionService requireSubmissionService(
            ObjectProvider<BrokerOrderSubmissionService> submissionServiceProvider
    ) {
        BrokerOrderSubmissionService submissionService =
                submissionServiceProvider.getIfAvailable();
        if (submissionService == null) {
            throw new IllegalStateException(
                    "BrokerOrderSubmissionService must be configured "
                            + "when trade execution mode is BROKER."
            );
        }
        return submissionService;
    }
}
