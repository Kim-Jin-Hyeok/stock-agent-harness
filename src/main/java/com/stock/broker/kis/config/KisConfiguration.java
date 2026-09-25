package com.stock.broker.kis.config;

import com.stock.broker.kis.account.KisAccountBalanceClient;
import com.stock.broker.kis.account.provider.KisBrokerAccountProvider;
import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.order.KisCashOrderClient;
import com.stock.broker.kis.order.cancellation.KisOrderCancellationClient;
import com.stock.broker.kis.order.cancellation.inquiry.KisCancelableOrderInquiryClient;
import com.stock.broker.kis.order.cancellation.provider.KisBrokerOrderCancellationProvider;
import com.stock.broker.kis.order.inquiry.KisOrderInquiryClient;
import com.stock.broker.kis.order.inquiry.provider.KisBrokerOrderInquiryProvider;
import com.stock.broker.kis.order.provider.KisBrokerOrderProvider;
import com.stock.broker.order.application.BrokerOrderPortfolioApplicationService;
import com.stock.broker.order.application.BrokerOrderReconciliationService;
import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.broker.order.cancellation.application.BrokerOrderExpirationCancellationService;
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;
import com.stock.broker.order.cancellation.scheduler.BrokerOrderExpirationCancellationScheduler;
import com.stock.broker.order.cancellation.scheduler.config.BrokerOrderExpirationCancellationSchedulerProperties;
import com.stock.broker.order.config.BrokerOrderProperties;
import com.stock.broker.order.inquiry.provider.BrokerOrderInquiryProvider;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.broker.order.scheduler.BrokerOrderReconciliationScheduler;
import com.stock.broker.order.scheduler.config.BrokerOrderReconciliationSchedulerProperties;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionService;
import com.stock.market.price.history.collection.config.DailyPriceHistoryBootstrapProperties;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import com.stock.market.price.history.collection.runner.DailyPriceHistoryBootstrapRunner;
import com.stock.market.price.history.collection.scheduler.DailyPriceHistoryCollectionScheduler;
import com.stock.market.price.history.collection.scheduler.config.DailyPriceHistoryCollectionSchedulerProperties;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryClient;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryProvider;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryRequestWaiter;
import com.stock.market.price.provider.kis.KisCurrentPriceClient;
import com.stock.market.price.provider.kis.KisCurrentPriceProvider;
import com.stock.portfolio.PortfolioService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "broker.kis",
        name = "enabled",
        havingValue = "true"
)
public class KisConfiguration {

    @Bean
    public RestClient kisRestClient(KisProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .build();
    }

    @Bean
    public KisTokenClient kisTokenClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisTokenClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret()
        );
    }

    @Bean
    public KisTokenProvider kisTokenProvider(
            KisTokenClient kisTokenClient,
            Clock clock,
            KisProperties properties
    ) {
        return new KisTokenProvider(
                kisTokenClient,
                clock,
                properties.tokenRefreshBeforeExpiration()
        );
    }

    @Bean
    public KisCurrentPriceClient kisCurrentPriceClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisCurrentPriceClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret()
        );
    }

    @Bean
    public KisCurrentPriceProvider kisCurrentPriceProvider(
            KisCurrentPriceClient kisCurrentPriceClient,
            KisTokenProvider kisTokenProvider,
            Clock clock
    ) {
        return new KisCurrentPriceProvider(
                kisCurrentPriceClient,
                kisTokenProvider,
                clock
        );
    }

    @Bean
    public KisDailyPriceHistoryClient kisDailyPriceHistoryClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisDailyPriceHistoryClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret(),
                properties.dailyPriceHistoryMarket()
        );
    }

    @Bean
    public KisDailyPriceHistoryProvider kisDailyPriceHistoryProvider(
            KisDailyPriceHistoryClient kisDailyPriceHistoryClient,
            KisTokenProvider kisTokenProvider,
            KisDailyPriceHistoryRequestWaiter requestWaiter,
            KisProperties properties
    ) {
        return new KisDailyPriceHistoryProvider(
                kisDailyPriceHistoryClient,
                kisTokenProvider,
                requestWaiter,
                properties.dailyPriceHistoryMaxPages()
        );
    }

    @Bean
    public KisDailyPriceHistoryRequestWaiter
    kisDailyPriceHistoryRequestWaiter(KisProperties properties) {
        return new KisDailyPriceHistoryRequestWaiter(
                properties.dailyPriceHistoryRequestDelay()
        );
    }

    @Bean
    public DailyPriceHistoryCollectionService
    dailyPriceHistoryCollectionService(
            DailyPriceHistoryProvider historyProvider,
            DailyPriceBarRepository dailyPriceBarRepository
    ) {
        return new DailyPriceHistoryCollectionService(
                historyProvider,
                dailyPriceBarRepository
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "market.price.history.collection.bootstrap",
            name = "enabled",
            havingValue = "true"
    )
    public DailyPriceHistoryBootstrapRunner dailyPriceHistoryBootstrapRunner(
            DailyPriceHistoryCollectionService collectionService,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            DailyPriceHistoryBootstrapProperties bootstrapProperties,
            DailyPriceHistoryCollectionProperties collectionProperties
    ) {
        return new DailyPriceHistoryBootstrapRunner(
                collectionService,
                collectionDatePolicy,
                bootstrapProperties,
                collectionProperties
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "market.price.history.collection.scheduler",
            name = "enabled",
            havingValue = "true"
    )
    public DailyPriceHistoryCollectionScheduler
    dailyPriceHistoryCollectionScheduler(
            DailyPriceHistoryCollectionService collectionService,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            DailyPriceHistoryCollectionProperties collectionProperties,
            DailyPriceHistoryCollectionSchedulerProperties schedulerProperties
    ) {
        return new DailyPriceHistoryCollectionScheduler(
                collectionService,
                collectionDatePolicy,
                collectionProperties,
                schedulerProperties
        );
    }

    @Bean
    public KisAccountBalanceClient kisAccountBalanceClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisAccountBalanceClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret(),
                properties.accountBalanceMaxPages()
        );
    }

    @Bean
    public KisBrokerAccountProvider kisBrokerAccountProvider(
            KisAccountBalanceClient kisAccountBalanceClient,
            KisTokenProvider kisTokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        return new KisBrokerAccountProvider(
                kisAccountBalanceClient,
                kisTokenProvider,
                properties,
                clock
        );
    }

    @Bean
    public KisCashOrderClient kisCashOrderClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisCashOrderClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret()
        );
    }

    @Bean
    public KisOrderInquiryClient kisOrderInquiryClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisOrderInquiryClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret(),
                properties.orderInquiryMaxPages()
        );
    }

    @Bean
    public KisCancelableOrderInquiryClient kisCancelableOrderInquiryClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisCancelableOrderInquiryClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret(),
                properties.cancelableOrderInquiryMaxPages()
        );
    }

    @Bean
    public KisOrderCancellationClient kisOrderCancellationClient(
            RestClient kisRestClient,
            KisProperties properties
    ) {
        return new KisOrderCancellationClient(
                kisRestClient,
                properties.appKey(),
                properties.appSecret()
        );
    }

    @Bean
    public KisBrokerOrderProvider kisBrokerOrderProvider(
            KisCashOrderClient kisCashOrderClient,
            KisTokenProvider kisTokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        return new KisBrokerOrderProvider(
                kisCashOrderClient,
                kisTokenProvider,
                properties,
                clock
        );
    }

    @Bean
    public KisBrokerOrderInquiryProvider kisBrokerOrderInquiryProvider(
            KisOrderInquiryClient kisOrderInquiryClient,
            KisTokenProvider kisTokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        return new KisBrokerOrderInquiryProvider(
                kisOrderInquiryClient,
                kisTokenProvider,
                properties,
                clock
        );
    }

    @Bean
    public KisBrokerOrderCancellationProvider
    kisBrokerOrderCancellationProvider(
            KisCancelableOrderInquiryClient cancelableOrderInquiryClient,
            KisOrderCancellationClient orderCancellationClient,
            KisTokenProvider kisTokenProvider,
            KisProperties properties,
            Clock clock
    ) {
        return new KisBrokerOrderCancellationProvider(
                cancelableOrderInquiryClient,
                orderCancellationClient,
                kisTokenProvider,
                properties,
                clock
        );
    }

    @Bean
    public BrokerOrderExpirationCancellationService
    brokerOrderExpirationCancellationService(
            BrokerOrderCancellationProvider cancellationProvider,
            BrokerOrderRepository brokerOrderRepository,
            Clock clock
    ) {
        return new BrokerOrderExpirationCancellationService(
                cancellationProvider,
                brokerOrderRepository,
                clock
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "broker.order.cancellation.scheduler",
            name = "enabled",
            havingValue = "true"
    )
    public BrokerOrderExpirationCancellationScheduler
    brokerOrderExpirationCancellationScheduler(
            BrokerOrderExpirationCancellationService cancellationService,
            BrokerOrderExpirationCancellationSchedulerProperties properties
    ) {
        return new BrokerOrderExpirationCancellationScheduler(
                cancellationService,
                properties
        );
    }

    @Bean
    public BrokerOrderSubmissionService brokerOrderSubmissionService(
            KisBrokerOrderProvider kisBrokerOrderProvider,
            BrokerOrderRepository brokerOrderRepository,
            BrokerOrderProperties brokerOrderProperties
    ) {
        return new BrokerOrderSubmissionService(
                kisBrokerOrderProvider,
                brokerOrderRepository,
                brokerOrderProperties
        );
    }

    @Bean
    public BrokerOrderReconciliationService brokerOrderReconciliationService(
            BrokerOrderInquiryProvider inquiryProvider,
            BrokerOrderRepository brokerOrderRepository
    ) {
        return new BrokerOrderReconciliationService(
                inquiryProvider,
                brokerOrderRepository
        );
    }

    @Bean
    public BrokerOrderPortfolioApplicationService
    brokerOrderPortfolioApplicationService(
            BrokerOrderRepository brokerOrderRepository,
            PortfolioService portfolioService
    ) {
        return new BrokerOrderPortfolioApplicationService(
                brokerOrderRepository,
                portfolioService
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "broker.order.reconciliation.scheduler",
            name = "enabled",
            havingValue = "true"
    )
    public BrokerOrderReconciliationScheduler
    brokerOrderReconciliationScheduler(
            BrokerOrderReconciliationService reconciliationService,
            BrokerOrderPortfolioApplicationService portfolioApplicationService,
            BrokerOrderReconciliationSchedulerProperties properties
    ) {
        return new BrokerOrderReconciliationScheduler(
                reconciliationService,
                portfolioApplicationService,
                properties
        );
    }
}
