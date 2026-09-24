package com.stock.broker.kis.config;

import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.broker.kis.account.KisAccountBalanceClient;
import com.stock.broker.kis.account.provider.KisBrokerAccountProvider;
import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.order.KisCashOrderClient;
import com.stock.broker.kis.order.cancellation.inquiry.KisCancelableOrderInquiryClient;
import com.stock.broker.kis.order.inquiry.KisOrderInquiryClient;
import com.stock.broker.kis.order.inquiry.provider.KisBrokerOrderInquiryProvider;
import com.stock.broker.kis.order.provider.KisBrokerOrderProvider;
import com.stock.broker.order.application.BrokerOrderReconciliationService;
import com.stock.broker.order.inquiry.provider.BrokerOrderInquiryProvider;
import com.stock.broker.order.provider.BrokerOrderProvider;
import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.broker.order.config.BrokerOrderProperties;
import com.stock.broker.order.persistence.BrokerOrderRepository;
import com.stock.broker.order.scheduler.BrokerOrderReconciliationScheduler;
import com.stock.broker.order.scheduler.config.BrokerOrderReconciliationSchedulerProperties;
import com.stock.market.price.provider.kis.KisCurrentPriceClient;
import com.stock.market.price.provider.CurrentPriceProvider;
import com.stock.market.price.provider.FixedCurrentPriceProvider;
import com.stock.market.price.provider.kis.KisCurrentPriceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KisConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            KisConfiguration.class,
                            FixedCurrentPriceProvider.class
                    )
                    .withBean(Clock.class, Clock::systemUTC)
                    .withBean(
                            BrokerOrderRepository.class,
                            () -> mock(BrokerOrderRepository.class)
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
                            BrokerOrderReconciliationScheduler.class
                    );
                });
    }

    @Test
    void createsSingletonKisBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("broker.kis.enabled=true")
                .withBean(KisProperties.class, this::enabledProperties)
                .run(context -> {
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
                10
        );
    }
}
