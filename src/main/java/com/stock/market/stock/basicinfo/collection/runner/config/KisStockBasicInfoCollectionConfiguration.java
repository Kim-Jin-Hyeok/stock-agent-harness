package com.stock.market.stock.basicinfo.collection.runner.config;

import com.stock.market.stock.basicinfo.collection.KisStockBasicInfoCollectionService;
import com.stock.market.stock.basicinfo.collection.runner.KisStockBasicInfoCollectionRunner;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.config.KisStockBasicInfoConfiguration;
import com.stock.market.stock.basicinfo.provider.kis.config.KisStockBasicInfoProperties;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Clock;
import java.util.List;

// Intentionally has no component stereotype: only the standalone entry point registers this configuration.
@ConditionalOnProperty(prefix = "market.stock.basic-info.collection.manual", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({KisStockBasicInfoCollectionProperties.class, KisStockBasicInfoProperties.class})
@Import({KisStockBasicInfoConfiguration.class, KisStockBasicInfoObservationStore.class})
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class})
@EntityScan(basePackageClasses = KisStockBasicInfoObservationEntity.class)
@EnableJpaRepositories(basePackageClasses = KisStockBasicInfoObservationRepository.class)
public class KisStockBasicInfoCollectionConfiguration {
    public KisStockBasicInfoCollectionConfiguration(
            KisStockBasicInfoCollectionProperties properties,
            KisStockBasicInfoProperties kisProperties,
            Environment environment
    ) {
        if (!properties.enabled() || !kisProperties.enabled()) {
            throw new IllegalArgumentException("Manual collection requires both manual and KIS basic info settings to be enabled.");
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("spring.datasource.url must be explicitly configured for manual collection.");
        }
    }

    @Bean
    public Clock kisStockBasicInfoCollectionClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HibernatePropertiesCustomizer kisStockBasicInfoCollectionSchemaValidation() {
        return properties -> {
            // Profile or command-line DDL options must not turn a collection run into schema maintenance.
            // Hibernate's ORM-specific action can override the global action, so pin both.
            for (String suffix : List.of("", ".orm")) {
                properties.put("hibernate.hbm2ddl.auto" + suffix, "validate");
                for (String prefix : List.of("jakarta.persistence", "javax.persistence")) {
                    properties.put(prefix + ".schema-generation.database.action" + suffix, "validate");
                    properties.put(prefix + ".schema-generation.scripts.action" + suffix, "none");
                }
            }
        };
    }

    @Bean
    public KisStockBasicInfoCollectionRunner kisStockBasicInfoCollectionRunner(
            KisStockBasicInfoCollectionService service,
            KisStockBasicInfoCollectionProperties properties
    ) {
        return new KisStockBasicInfoCollectionRunner(service, properties);
    }
}
