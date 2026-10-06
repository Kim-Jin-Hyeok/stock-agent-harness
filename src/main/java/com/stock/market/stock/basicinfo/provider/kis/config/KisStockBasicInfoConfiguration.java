package com.stock.market.stock.basicinfo.provider.kis.config;

import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KisStockBasicInfoProperties.class)
@ConditionalOnProperty(prefix = "market.stock.basic-info.kis", name = "enabled", havingValue = "true")
public class KisStockBasicInfoConfiguration {
    private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";

    @Bean(destroyMethod = "shutdownNow")
    public HttpClient kisStockBasicInfoHttpClient(KisStockBasicInfoProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    public RestClient kisStockBasicInfoRestClient(
            @Qualifier("kisStockBasicInfoHttpClient") HttpClient httpClient,
            KisStockBasicInfoProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        return RestClient.builder().baseUrl(BASE_URL).requestFactory(requestFactory).build();
    }

    @Bean
    public KisTokenClient kisStockBasicInfoTokenClient(
            @Qualifier("kisStockBasicInfoRestClient") RestClient restClient,
            KisStockBasicInfoProperties properties
    ) {
        return new KisTokenClient(restClient, properties.appKey(), properties.appSecret());
    }

    @Bean
    public KisTokenProvider kisStockBasicInfoTokenProvider(
            @Qualifier("kisStockBasicInfoTokenClient") KisTokenClient tokenClient,
            Clock clock,
            KisStockBasicInfoProperties properties
    ) {
        return new KisTokenProvider(tokenClient, clock, properties.tokenRefreshBeforeExpiration());
    }

    @Bean
    public KisStockBasicInfoClient kisStockBasicInfoClient(
            @Qualifier("kisStockBasicInfoRestClient") RestClient restClient,
            KisStockBasicInfoProperties properties,
            Clock clock
    ) {
        return new KisStockBasicInfoClient(restClient, properties.appKey(), properties.appSecret(), clock);
    }

    @Bean
    public KisStockBasicInfoProvider kisStockBasicInfoProvider(
            KisStockBasicInfoClient client,
            @Qualifier("kisStockBasicInfoTokenProvider") KisTokenProvider tokenProvider
    ) {
        return new KisStockBasicInfoProvider(client, tokenProvider);
    }
}
