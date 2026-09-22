package com.stock.broker.kis.config;

import com.stock.broker.account.provider.BrokerAccountProvider;
import com.stock.broker.kis.account.KisAccountBalanceClient;
import com.stock.broker.kis.account.provider.KisBrokerAccountProvider;
import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.price.provider.kis.KisCurrentPriceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KisConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(KisConfiguration.class);

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
                            KisAccountBalanceClient.class
                    );
                    assertThat(context).doesNotHaveBean(
                            BrokerAccountProvider.class
                    );
                });
    }

    @Test
    void createsSingletonKisBeansWhenEnabled() {
        contextRunner
                .withPropertyValues("broker.kis.enabled=true")
                .withBean(KisProperties.class, this::enabledProperties)
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(KisTokenClient.class);
                    assertThat(context).hasSingleBean(KisTokenProvider.class);
                    assertThat(context).hasSingleBean(
                            KisCurrentPriceClient.class
                    );
                    assertThat(context).hasSingleBean(
                            KisAccountBalanceClient.class
                    );
                    assertThat(context).hasSingleBean(
                            BrokerAccountProvider.class
                    );
                    assertThat(context.getBean(BrokerAccountProvider.class))
                            .isInstanceOf(KisBrokerAccountProvider.class);

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
                10
        );
    }
}
