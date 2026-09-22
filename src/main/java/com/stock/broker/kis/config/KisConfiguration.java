package com.stock.broker.kis.config;

import com.stock.broker.kis.account.KisAccountBalanceClient;
import com.stock.broker.kis.account.provider.KisBrokerAccountProvider;
import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.price.provider.kis.KisCurrentPriceClient;
import com.stock.market.price.provider.kis.KisCurrentPriceProvider;
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
}
