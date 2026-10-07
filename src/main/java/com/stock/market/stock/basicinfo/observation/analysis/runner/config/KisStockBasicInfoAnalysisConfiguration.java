package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.runner.KisStockBasicInfoAnalysisRunner;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.strategy.universe.eligibility.classification.kis.KisStockMasterTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.KisStockBasicInfoTypeClassificationPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.KisStockBasicInfoTypeResolutionPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
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

// Only the standalone entry point registers this configuration; normal component scanning must not discover it.
@ConditionalOnProperty(prefix = "market.stock.basic-info.analysis.manual", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KisStockBasicInfoAnalysisProperties.class)
@Import(KisStockBasicInfoObservationStore.class)
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class})
@EntityScan(basePackageClasses = KisStockBasicInfoObservationEntity.class)
@EnableJpaRepositories(basePackageClasses = KisStockBasicInfoObservationRepository.class)
public class KisStockBasicInfoAnalysisConfiguration {
    public KisStockBasicInfoAnalysisConfiguration(KisStockBasicInfoAnalysisProperties properties, Environment environment) {
        if (!properties.enabled()) {
            throw new IllegalArgumentException("Manual analysis must be enabled.");
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("spring.datasource.url must be explicitly configured for manual analysis.");
        }
    }

    @Bean
    public Clock kisStockBasicInfoAnalysisClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HibernatePropertiesCustomizer kisStockBasicInfoAnalysisSchemaValidation() {
        return properties -> {
            // ORM-specific and legacy JPA options must not override validation or generate SQL scripts.
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
    public StockMasterBatchParsingService kisStockBasicInfoAnalysisMasterParser() {
        return new StockMasterBatchParsingService(new KisStockMasterParser());
    }

    @Bean
    public KisStockBasicInfoAnalysisService kisStockBasicInfoAnalysisService(KisStockBasicInfoObservationStore store) {
        return new KisStockBasicInfoAnalysisService(store, new KisStockBasicInfoParser(), new KisStockBasicInfoMatchingPolicy(),
                new KisStockBasicInfoTypeResolutionPolicy(new KisStockMasterTypeClassificationPolicy(), new KisStockBasicInfoTypeClassificationPolicy()),
                new KisStockBasicInfoRestrictionObservationPolicy(), new KisStockBasicInfoRestrictionScreeningPolicy());
    }

    @Bean
    public KisStockBasicInfoAnalysisRunner kisStockBasicInfoAnalysisRunner(
            StockMasterBatchParsingService masterParser,
            KisStockBasicInfoAnalysisService service,
            KisStockBasicInfoAnalysisProperties properties
    ) {
        return new KisStockBasicInfoAnalysisRunner(masterParser, service, properties);
    }
}
