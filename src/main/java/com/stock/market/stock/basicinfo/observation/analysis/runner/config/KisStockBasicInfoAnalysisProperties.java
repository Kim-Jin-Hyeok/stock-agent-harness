package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.UUID;

@ConfigurationProperties(prefix = "market.stock.basic-info.analysis.manual")
public record KisStockBasicInfoAnalysisProperties(
        boolean enabled,
        Long observationId,
        String observationRoot,
        UUID collectionId
) {
    public KisStockBasicInfoAnalysisProperties {
        if (enabled) {
            if (observationId == null || observationId <= 0) {
                throw new IllegalArgumentException("observationId must be positive.");
            }
            if (observationRoot == null || observationRoot.isBlank()) {
                throw new IllegalArgumentException("observationRoot must not be blank.");
            }
            Path.of(observationRoot);
            if (collectionId == null) {
                throw new IllegalArgumentException("collectionId must not be null.");
            }
        }
    }
}
