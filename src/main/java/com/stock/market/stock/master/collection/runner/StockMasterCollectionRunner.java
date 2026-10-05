package com.stock.market.stock.master.collection.runner;

import com.stock.market.stock.master.collection.StockMasterCollectionService;
import com.stock.market.stock.master.collection.runner.config.StockMasterCollectionConfiguration;
import com.stock.market.stock.master.collection.runner.config.StockMasterCollectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

@Slf4j
public class StockMasterCollectionRunner implements ApplicationRunner {
    private final StockMasterCollectionService service;
    private final StockMasterCollectionProperties properties;

    public StockMasterCollectionRunner(StockMasterCollectionService service, StockMasterCollectionProperties properties) {
        this.service = Objects.requireNonNull(service, "service must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        if (!properties.enabled()) {
            return;
        }
        var result = service.collect(Path.of(properties.outputDirectory()));
        log.info("Stock master collection completed. collectionId={}, marketCount={}, directory={}",
                result.collectionId(), result.files().size(),
                Path.of(properties.outputDirectory()).toAbsolutePath().normalize().resolve(result.collectionId().toString()));
    }

    public static void main(String[] args) {
        // No application scan or auto-configuration: this entry point cannot start DB, broker or scheduler beans.
        try (var context = new SpringApplicationBuilder(StockMasterCollectionConfiguration.class)
                .web(WebApplicationType.NONE).run(args)) {
            log.info("Stock master manual process finished.");
        }
    }
}
