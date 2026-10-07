package com.stock.market.stock.basicinfo.collection.runner;

import com.stock.market.stock.basicinfo.collection.KisStockBasicInfoCollectionService;
import com.stock.market.stock.basicinfo.collection.runner.config.KisStockBasicInfoCollectionConfiguration;
import com.stock.market.stock.basicinfo.collection.runner.config.KisStockBasicInfoCollectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Objects;

@Slf4j
public class KisStockBasicInfoCollectionRunner implements ApplicationRunner {
    private final KisStockBasicInfoCollectionService service;
    private final KisStockBasicInfoCollectionProperties properties;

    public KisStockBasicInfoCollectionRunner(KisStockBasicInfoCollectionService service, KisStockBasicInfoCollectionProperties properties) {
        this.service = Objects.requireNonNull(service, "service must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }
        Long observationId = service.collect(properties.symbol());
        if (observationId == null || observationId <= 0) {
            throw new IllegalStateException("Saved observationId must be positive.");
        }
        log.info("Stock basic info raw observation saved. symbol={}, observationId={}", properties.symbol(), observationId);
    }

    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(KisStockBasicInfoCollectionConfiguration.class)
                .web(WebApplicationType.NONE).run(args)) {
            log.info("Stock basic info manual process finished.");
        }
    }
}
