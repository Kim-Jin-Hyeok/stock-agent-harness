package com.stock.market.stock.master.collection.runner.config;

import com.stock.market.stock.master.collection.StockMasterCollectionService;
import com.stock.market.stock.master.collection.runner.StockMasterCollectionRunner;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StockMasterCollectionProperties.class)
@ConditionalOnProperty(prefix = "market.stock.master.collection.manual", name = "enabled", havingValue = "true")
public class StockMasterCollectionConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    public HttpClient stockMasterHttpClient(StockMasterCollectionProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    public KisStockMasterClient kisStockMasterClient(
            @Qualifier("stockMasterHttpClient") HttpClient stockMasterHttpClient,
            StockMasterCollectionProperties properties
    ) {
        return new KisStockMasterClient(stockMasterHttpClient, properties.downloadTimeout(), properties.maxArchiveBytes());
    }

    @Bean
    public StockMasterCollectionService stockMasterCollectionService(
            KisStockMasterClient client, StockMasterCollectionProperties properties
    ) {
        return new StockMasterCollectionService(client, Clock.systemUTC(), properties.maxExtractedBytes());
    }

    @Bean
    public StockMasterCollectionRunner stockMasterCollectionRunner(
            StockMasterCollectionService service, StockMasterCollectionProperties properties
    ) {
        return new StockMasterCollectionRunner(service, properties);
    }
}
