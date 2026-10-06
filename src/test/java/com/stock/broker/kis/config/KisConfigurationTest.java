package com.stock.broker.kis.config;

import com.stock.broker.account.provider.BrokerAccountProvider;
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
import com.stock.broker.order.cancellation.application.BrokerOrderExpirationCancellationService;
import com.stock.broker.order.cancellation.scheduler.BrokerOrderExpirationCancellationScheduler;
import com.stock.broker.order.cancellation.scheduler.config.BrokerOrderExpirationCancellationSchedulerProperties;
import com.stock.broker.order.inquiry.provider.BrokerOrderInquiryProvider;
import com.stock.broker.order.provider.BrokerOrderProvider;
import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.broker.order.config.BrokerOrderProperties;
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.broker.order.scheduler.BrokerOrderReconciliationScheduler;
import com.stock.broker.order.scheduler.config.BrokerOrderReconciliationSchedulerProperties;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionService;
import com.stock.market.index.history.collection.backfill.MarketIndexDailyHistoryBackfillService;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryBootstrapProperties;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryCollectionProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import com.stock.market.index.history.collection.runner.MarketIndexDailyHistoryBootstrapRunner;
import com.stock.market.index.history.collection.scheduler.MarketIndexDailyHistoryCollectionScheduler;
import com.stock.market.index.history.collection.scheduler.config.MarketIndexDailyHistoryCollectionSchedulerProperties;
import com.stock.market.index.history.persistence.MarketIndexDailyObservationRepository;
import com.stock.market.index.history.provider.MarketIndexDailyHistoryProvider;
import com.stock.market.index.history.provider.kis.KisMarketIndexDailyHistoryClient;
import com.stock.market.index.history.provider.kis.KisMarketIndexDailyHistoryProvider;
import com.stock.market.index.history.provider.kis.KisMarketIndexDailyHistoryRequestWaiter;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionService;
import com.stock.market.price.history.collection.backfill.DailyPriceTradingValueBackfillService;
import com.stock.market.price.history.collection.backfill.runner.DailyPriceTradingValueBackfillRunner;
import com.stock.market.price.history.collection.backfill.runner.config.DailyPriceTradingValueBackfillProperties;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.collection.config.DailyPriceHistoryBootstrapProperties;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import com.stock.market.price.history.collection.runner.DailyPriceHistoryBootstrapRunner;
import com.stock.market.price.history.collection.scheduler.DailyPriceHistoryCollectionScheduler;
import com.stock.market.price.history.collection.scheduler.config.DailyPriceHistoryCollectionSchedulerProperties;
import com.stock.market.price.history.persistence.DailyPriceBarRepository;
import com.stock.market.price.history.provider.DailyPriceHistoryProvider;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryClient;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryMarket;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryProvider;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryRequestWaiter;
import com.stock.market.price.provider.kis.KisCurrentPriceClient;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.FixedCurrentPriceProvider;
import com.stock.market.price.provider.kis.KisCurrentPriceProvider;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.config.KisStockBasicInfoConfiguration;
import com.stock.market.stock.basicinfo.provider.kis.config.support.KisStockBasicInfoMockHttp;
import com.stock.portfolio.PortfolioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KisConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            KisConfiguration.class,
                            FixedCurrentPriceProvider.class
                    )
                    .withBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-06T01:00:00Z"), ZoneOffset.UTC))
                    .withBean(EntityManager.class, () -> mock(EntityManager.class))
                    .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                    .withBean(
                            BrokerOrderRepository.class,
                            () -> mock(BrokerOrderRepository.class)
                    )
                    .withBean(
                            PortfolioService.class,
                            () -> mock(PortfolioService.class)
                    )
                    .withBean(
                            DailyPriceBarRepository.class,
                            () -> mock(DailyPriceBarRepository.class)
                    )
                    .withBean(
                            MarketIndexDailyObservationRepository.class,
                            () -> mock(
                                    MarketIndexDailyObservationRepository.class
                            )
                    )
                    .withBean(
                            BrokerOrderProperties.class,
                            () -> new BrokerOrderProperties(
                                    Duration.ofMinutes(5)
                            )
                    )
                    .withBean(
                            BrokerOrderReconciliationSchedulerProperties.class,
                            () -> new BrokerOrderReconciliationSchedulerProperties(
                                    false,
                                    10_000L
                            )
                    )
                    .withBean(
                            BrokerOrderExpirationCancellationSchedulerProperties.class,
                            () -> new BrokerOrderExpirationCancellationSchedulerProperties(
                                    false,
                                    10_000L
                            )
                    );

    @Test
    void doesNotCreateKisBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("broker.kis.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).doesNotHaveBean(KisTokenClient.class);
                    assertThat(context).doesNotHaveBean(KisTokenProvider.class);
                    assertThat(context).doesNotHaveBean(
                            KisCurrentPriceClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisDailyPriceHistoryClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisMarketIndexDailyHistoryClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            MarketIndexDailyHistoryProvider.class
                    );
                    assertThat(context).doesNotHaveBean(
                            MarketIndexDailyHistoryCollectionService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            MarketIndexDailyHistoryBackfillService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            MarketIndexDailyHistoryBootstrapRunner.class
                    );
                    assertThat(context).doesNotHaveBean(
                            MarketIndexDailyHistoryCollectionScheduler.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisMarketIndexDailyHistoryRequestWaiter.class
                    );
                    assertThat(context).doesNotHaveBean(
                            DailyPriceHistoryProvider.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisDailyPriceHistoryRequestWaiter.class
                    );
                    assertThat(context).doesNotHaveBean(
                            DailyPriceHistoryCollectionService.class
                    );
                    assertThat(context).doesNotHaveBean(DailyPriceTradingValueBackfillService.class);
                    assertThat(context).doesNotHaveBean(
                            DailyPriceHistoryBootstrapRunner.class
                    );
                    assertThat(context).doesNotHaveBean(
                            DailyPriceHistoryCollectionScheduler.class
                    );
                    assertThat(context).hasSingleBean(
                            CurrentPriceProvider.class
                    );
                    assertThat(context.getBean(CurrentPriceProvider.class))
                            .isInstanceOf(FixedCurrentPriceProvider.class);
                    assertThat(context).doesNotHaveBean(
                            KisAccountBalanceClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerAccountProvider.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisCashOrderClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisOrderInquiryClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisCancelableOrderInquiryClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            KisOrderCancellationClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderCancellationProvider.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderExpirationCancellationService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderExpirationCancellationScheduler.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderInquiryProvider.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderProvider.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderSubmissionService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderReconciliationService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderPortfolioApplicationService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderReconciliationScheduler.class
                    );
                });
    }

    @Test
    void doesNotCreateOrderSchedulerBeansWhenSchedulersAreDisabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "broker.order.reconciliation.scheduler.enabled=false",
                        "broker.order.cancellation.scheduler.enabled=false"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .run(context -> {
                    assertThat(context).hasSingleBean(
                            BrokerOrderReconciliationService.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderPortfolioApplicationService.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderExpirationCancellationService.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderReconciliationScheduler.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerOrderExpirationCancellationScheduler.class
                    );
                });
    }

    @Test
    void wiresTradingValueBackfillRunnerToExistingKisServiceWhenExplicitlyEnabled() {
        contextRunner
                .withUserConfiguration(DailyPriceTradingValueBackfillRunner.class)
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "market.price.history.collection.trading-value-backfill.enabled=true"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .withBean(DailyPriceCollectionDatePolicy.class,
                        () -> mock(DailyPriceCollectionDatePolicy.class))
                .withBean(DailyPriceTradingValueBackfillProperties.class,
                        () -> new DailyPriceTradingValueBackfillProperties(
                                true, "005930", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23),
                                TradingVenueScope.INTEGRATED, 3))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DailyPriceTradingValueBackfillRunner.class);
                    assertThat(context).hasSingleBean(DailyPriceTradingValueBackfillService.class);
                    assertThat(context).hasSingleBean(KisDailyPriceHistoryProvider.class);
                });
    }

    @Test
    void createsDailyPriceHistoryBootstrapRunnerWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "market.price.history.collection.bootstrap.enabled=true"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .withBean(
                        DailyPriceCollectionDatePolicy.class,
                        () -> mock(DailyPriceCollectionDatePolicy.class)
                )
                .withBean(
                        DailyPriceHistoryBootstrapProperties.class,
                        () -> new DailyPriceHistoryBootstrapProperties(true)
                )
                .withBean(
                        DailyPriceHistoryCollectionProperties.class,
                        this::collectionProperties
                )
                .run(context -> assertThat(context).hasSingleBean(
                        DailyPriceHistoryBootstrapRunner.class
                ));
    }

    @Test
    void createsMarketIndexDailyHistoryBootstrapRunnerWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "market.index.history.collection.bootstrap.enabled=true"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .withBean(
                        MarketIndexDailyHistoryCollectionDatePolicy.class,
                        () -> mock(
                                MarketIndexDailyHistoryCollectionDatePolicy.class
                        )
                )
                .withBean(
                        MarketIndexDailyHistoryBootstrapProperties.class,
                        () -> new MarketIndexDailyHistoryBootstrapProperties(
                                true
                        )
                )
                .withBean(
                        MarketIndexDailyHistoryCollectionProperties.class,
                        this::marketIndexCollectionProperties
                )
                .run(context -> assertThat(context).hasSingleBean(
                        MarketIndexDailyHistoryBootstrapRunner.class
                ));
    }

    @Test
    void doesNotCreateMarketIndexDailyHistoryCollectionSchedulerWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "market.index.history.collection.scheduler.enabled=false"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .run(context -> assertThat(context).doesNotHaveBean(
                        MarketIndexDailyHistoryCollectionScheduler.class
                ));
    }

    @Test
    void createsMarketIndexDailyHistoryCollectionSchedulerWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "market.index.history.collection.scheduler.enabled=true"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .withBean(
                        MarketIndexDailyHistoryCollectionDatePolicy.class,
                        () -> mock(
                                MarketIndexDailyHistoryCollectionDatePolicy.class
                        )
                )
                .withBean(
                        MarketIndexDailyHistoryCollectionProperties.class,
                        this::marketIndexCollectionProperties
                )
                .withBean(
                        MarketIndexDailyHistoryCollectionSchedulerProperties.class,
                        () -> new MarketIndexDailyHistoryCollectionSchedulerProperties(
                                true,
                                "0 25 20 * * MON-FRI",
                                "Asia/Seoul"
                        )
                )
                .run(context -> assertThat(context).hasSingleBean(
                        MarketIndexDailyHistoryCollectionScheduler.class
                ));
    }

    @Test
    void createsDailyPriceHistoryCollectionSchedulerWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "market.price.history.collection.scheduler.enabled=true"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .withBean(
                        DailyPriceCollectionDatePolicy.class,
                        () -> mock(DailyPriceCollectionDatePolicy.class)
                )
                .withBean(
                        DailyPriceHistoryCollectionProperties.class,
                        this::collectionProperties
                )
                .withBean(
                        DailyPriceHistoryCollectionSchedulerProperties.class,
                        () -> schedulerProperties(true)
                )
                .run(context -> assertThat(context).hasSingleBean(
                        DailyPriceHistoryCollectionScheduler.class
                ));
    }

    @Test
    void createsSingletonKisBeansWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "broker.kis.enabled=true",
                        "broker.order.reconciliation.scheduler.enabled=true",
                        "broker.order.cancellation.scheduler.enabled=true"
                )
                .withBean(KisProperties.class, this::enabledProperties)
                .run(context -> {
                    assertThat(context).hasSingleBean(DailyPriceTradingValueBackfillService.class);
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(KisTokenClient.class);
                    assertThat(context).hasSingleBean(KisTokenProvider.class);
                    assertThat(context).hasSingleBean(
                            KisCurrentPriceClient.class
                    );
                    assertThat(context).hasSingleBean(
                            CurrentPriceProvider.class
                    );
                    assertThat(context.getBean(CurrentPriceProvider.class))
                            .isInstanceOf(KisCurrentPriceProvider.class);
                    assertThat(context).hasSingleBean(
                            KisDailyPriceHistoryClient.class
                    );
                    assertThat(context).hasSingleBean(
                            KisMarketIndexDailyHistoryClient.class
                    );
                    assertThat(context).hasSingleBean(
                            MarketIndexDailyHistoryProvider.class
                    );
                    assertThat(context.getBean(
                            MarketIndexDailyHistoryProvider.class
                    )).isInstanceOf(
                            KisMarketIndexDailyHistoryProvider.class
                    );
                    assertThat(context).hasSingleBean(
                            MarketIndexDailyHistoryCollectionService.class
                    );
                    assertThat(context).hasSingleBean(
                            MarketIndexDailyHistoryBackfillService.class
                    );
                    assertThat(context).hasSingleBean(
                            KisMarketIndexDailyHistoryRequestWaiter.class
                    );
                    assertThat(context).hasSingleBean(
                            DailyPriceHistoryProvider.class
                    );
                    assertThat(context).hasSingleBean(
                            KisDailyPriceHistoryRequestWaiter.class
                    );
                    assertThat(context.getBean(
                            DailyPriceHistoryProvider.class
                    )).isInstanceOf(KisDailyPriceHistoryProvider.class);
                    assertThat(context).hasSingleBean(
                            DailyPriceHistoryCollectionService.class
                    );
                    assertThat(context).hasSingleBean(
                            KisAccountBalanceClient.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerAccountProvider.class
                    );
                    assertThat(context.getBean(BrokerAccountProvider.class))
                            .isInstanceOf(KisBrokerAccountProvider.class);
                    assertThat(context).hasSingleBean(
                            KisCashOrderClient.class
                    );
                    assertThat(context).hasSingleBean(
                            KisOrderInquiryClient.class
                    );
                    assertThat(context).hasSingleBean(
                            KisCancelableOrderInquiryClient.class
                    );
                    assertThat(context).hasSingleBean(
                            KisOrderCancellationClient.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderCancellationProvider.class
                    );
                    assertThat(context.getBean(
                            BrokerOrderCancellationProvider.class
                    )).isInstanceOf(
                            KisBrokerOrderCancellationProvider.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderExpirationCancellationService.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderExpirationCancellationScheduler.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderInquiryProvider.class
                    );
                    assertThat(context.getBean(
                            BrokerOrderInquiryProvider.class
                    )).isInstanceOf(KisBrokerOrderInquiryProvider.class);
                    assertThat(context).hasSingleBean(
                            BrokerOrderProvider.class
                    );
                    assertThat(context.getBean(BrokerOrderProvider.class))
                            .isInstanceOf(KisBrokerOrderProvider.class);
                    assertThat(context).hasSingleBean(
                            BrokerOrderSubmissionService.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderReconciliationService.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderPortfolioApplicationService.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerOrderReconciliationScheduler.class
                    );

                    KisAccountBalanceClient first = context.getBean(
                            KisAccountBalanceClient.class
                    );
                    KisAccountBalanceClient second = context.getBean(
                            KisAccountBalanceClient.class
                    );
                    assertThat(first).isSameAs(second);
                });
    }

    @Test
    void preservesBrokerBeansWhenBasicInfoConfigurationIsDisabledWithoutReadOnlyCredentials() {
        var http = new KisStockBasicInfoMockHttp();
        contextRunner.withUserConfiguration(KisStockBasicInfoConfiguration.class)
                .withInitializer(context -> context.getBeanFactory().addBeanPostProcessor(http))
                .withPropertyValues("broker.kis.enabled=true", "market.stock.basic-info.kis.enabled=false")
                .withBean(KisProperties.class, this::enabledProperties)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(RestClient.class)
                            .hasSingleBean(KisTokenClient.class).hasSingleBean(KisTokenProvider.class)
                            .hasSingleBean(KisBrokerOrderProvider.class).doesNotHaveBean(KisStockBasicInfoClient.class)
                            .doesNotHaveBean(KisStockBasicInfoProvider.class);
                    http.verify();
                });
    }

    @Test
    void isolatesBothAuthenticationGraphsWithoutIssuingRequestsAtStartup() {
        var http = new KisStockBasicInfoMockHttp();
        withBothConnections(http).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KisStockBasicInfoClient.class)
                    .hasSingleBean(KisStockBasicInfoProvider.class);
            assertThat(context.getBeansOfType(RestClient.class)).hasSize(2);
            assertThat(context.getBeansOfType(KisTokenClient.class)).hasSize(2);
            assertThat(context.getBeansOfType(KisTokenProvider.class)).hasSize(2);
            var brokerRestClient = context.getBean("kisRestClient", RestClient.class);
            var readOnlyRestClient = context.getBean("kisStockBasicInfoRestClient", RestClient.class);
            var brokerTokenClient = context.getBean("kisTokenClient", KisTokenClient.class);
            var readOnlyTokenClient = context.getBean("kisStockBasicInfoTokenClient", KisTokenClient.class);
            var brokerTokenProvider = context.getBean("kisTokenProvider", KisTokenProvider.class);
            var readOnlyTokenProvider = context.getBean("kisStockBasicInfoTokenProvider", KisTokenProvider.class);
            assertThat(brokerRestClient).isNotSameAs(readOnlyRestClient);
            assertThat(brokerTokenClient).isNotSameAs(readOnlyTokenClient);
            assertThat(brokerTokenProvider).isNotSameAs(readOnlyTokenProvider);
            assertThat(ReflectionTestUtils.getField(brokerTokenProvider, "tokenClient")).isSameAs(brokerTokenClient);
            assertThat(ReflectionTestUtils.getField(readOnlyTokenProvider, "tokenClient")).isSameAs(readOnlyTokenClient);
            assertThat(ReflectionTestUtils.getField(brokerTokenClient, "restClient")).isSameAs(brokerRestClient);
            assertThat(ReflectionTestUtils.getField(readOnlyTokenClient, "restClient")).isSameAs(readOnlyRestClient);
            assertThat(ReflectionTestUtils.getField(context.getBean(KisStockBasicInfoClient.class), "restClient"))
                    .isSameAs(readOnlyRestClient);
            var basicInfoProvider = context.getBean(KisStockBasicInfoProvider.class);
            assertThat(ReflectionTestUtils.getField(basicInfoProvider, "client"))
                    .isSameAs(context.getBean(KisStockBasicInfoClient.class));
            assertThat(ReflectionTestUtils.getField(basicInfoProvider, "tokenProvider")).isSameAs(readOnlyTokenProvider);
            for (Class<?> type : List.of(KisCurrentPriceClient.class, KisDailyPriceHistoryClient.class,
                    KisMarketIndexDailyHistoryClient.class, KisAccountBalanceClient.class, KisCashOrderClient.class,
                    KisOrderInquiryClient.class, KisCancelableOrderInquiryClient.class, KisOrderCancellationClient.class)) {
                assertThat(ReflectionTestUtils.getField(context.getBean(type), "restClient")).isSameAs(brokerRestClient);
            }
            for (Class<?> type : List.of(KisCurrentPriceProvider.class, KisDailyPriceHistoryProvider.class,
                    KisMarketIndexDailyHistoryProvider.class, KisBrokerAccountProvider.class, KisBrokerOrderProvider.class,
                    KisBrokerOrderInquiryProvider.class, KisBrokerOrderCancellationProvider.class)) {
                assertThat(ReflectionTestUtils.getField(context.getBean(type), "tokenProvider")).isSameAs(brokerTokenProvider);
            }
            http.verify();
        });
    }

    @Test
    void keepsBrokerAndReadOnlyCredentialsRequestsAndTokenCachesIndependent() {
        var http = new KisStockBasicInfoMockHttp();
        withBothConnections(http).run(context -> {
            assertThat(context).hasNotFailed();
            http.verify();
            var brokerServer = http.server("kisRestClient");
            var readOnlyServer = http.server("kisStockBasicInfoRestClient");
            brokerServer.expect(requestTo("https://openapivts.koreainvestment.com:29443/oauth2/tokenP"))
                    .andExpect(method(POST))
                    .andExpect(content().json("""
                            {"grant_type":"client_credentials","appkey":"test-app-key","appsecret":"test-app-secret"}
                            """))
                    .andRespond(withSuccess(tokenResponse("synthetic-broker-token"), MediaType.APPLICATION_JSON));
            readOnlyServer.expect(requestTo("https://openapi.koreainvestment.com:9443/oauth2/tokenP"))
                    .andExpect(method(POST))
                    .andExpect(content().json("""
                            {"grant_type":"client_credentials","appkey":"synthetic-readonly-key",
                             "appsecret":"synthetic-readonly-secret"}
                            """))
                    .andRespond(withSuccess(tokenResponse("synthetic-readonly-token"), MediaType.APPLICATION_JSON));
            readOnlyServer.expect(requestTo("https://openapi.koreainvestment.com:9443"
                            + "/uapi/domestic-stock/v1/quotations/search-stock-info?PRDT_TYPE_CD=300&PDNO=005930"))
                    .andExpect(method(GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-readonly-token"))
                    .andExpect(header("appkey", "synthetic-readonly-key"))
                    .andExpect(header("appsecret", "synthetic-readonly-secret"))
                    .andRespond(withSuccess("{\"synthetic\":\"basic-info\"}", MediaType.APPLICATION_JSON));
            var brokerProvider = context.getBean("kisTokenProvider", KisTokenProvider.class);
            var readOnlyProvider = context.getBean("kisStockBasicInfoTokenProvider", KisTokenProvider.class);
            assertThat(brokerProvider.getAccessToken()).isEqualTo("synthetic-broker-token");
            assertThat(readOnlyProvider.getAccessToken()).isEqualTo("synthetic-readonly-token");
            assertThat(brokerProvider.getAccessToken()).isEqualTo("synthetic-broker-token");
            assertThat(readOnlyProvider.getAccessToken()).isEqualTo("synthetic-readonly-token");
            var response = context.getBean(KisStockBasicInfoProvider.class).getStockBasicInfo("005930");
            assertThat(response.requestedSymbol()).isEqualTo("005930");
            http.verify();
        });
    }

    private ApplicationContextRunner withBothConnections(KisStockBasicInfoMockHttp http) {
        return contextRunner.withUserConfiguration(KisStockBasicInfoConfiguration.class)
                .withInitializer(context -> context.getBeanFactory().addBeanPostProcessor(http))
                .withPropertyValues("broker.kis.enabled=true", "market.stock.basic-info.kis.enabled=true",
                        "market.stock.basic-info.kis.app-key=synthetic-readonly-key",
                        "market.stock.basic-info.kis.app-secret=synthetic-readonly-secret")
                .withBean(KisProperties.class, this::enabledProperties);
    }

    private String tokenResponse(String accessToken) {
        return """
                {"access_token":"%s","token_type":"Bearer","expires_in":3600,
                 "access_token_token_expired":"2026-10-06 11:00:00"}
                """.formatted(accessToken);
    }

    private KisProperties enabledProperties() {
        return new KisProperties(
                true,
                URI.create("https://openapivts.koreainvestment.com:29443"),
                "test-app-key",
                "test-app-secret",
                "12345678",
                "01",
                Duration.ofMinutes(1),
                10,
                10,
                10,
                10,
                Duration.ofSeconds(1),
                KisDailyPriceHistoryMarket.INTEGRATED
        );
    }

    private DailyPriceHistoryCollectionProperties collectionProperties() {
        return new DailyPriceHistoryCollectionProperties(
                LocalTime.of(20, 10),
                List.of("005930"),
                3
        );
    }

    private MarketIndexDailyHistoryCollectionProperties
    marketIndexCollectionProperties() {
        return new MarketIndexDailyHistoryCollectionProperties(
                LocalTime.of(20, 10),
                List.of("KOSPI"),
                3
        );
    }

    private DailyPriceHistoryCollectionSchedulerProperties
    schedulerProperties(boolean enabled) {
        return new DailyPriceHistoryCollectionSchedulerProperties(
                enabled,
                "0 15 20 * * MON-FRI",
                "Asia/Seoul"
        );
    }
}
