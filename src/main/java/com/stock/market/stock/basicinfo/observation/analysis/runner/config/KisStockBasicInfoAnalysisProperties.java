package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;
import java.util.UUID;

@ConfigurationProperties(prefix = "market.stock.basic-info.analysis.manual")
public record KisStockBasicInfoAnalysisProperties(
        boolean enabled,
        Long observationId,
        String observationRoot,
        UUID collectionId,
        boolean includeMarketWarnings,
        KisStockMasterMarket warningMarket
) {
    public KisStockBasicInfoAnalysisProperties(boolean enabled, Long observationId, String observationRoot, UUID collectionId) {
        this(enabled, observationId, observationRoot, collectionId, false, null);
    }

    @ConstructorBinding
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
            if (includeMarketWarnings && warningMarket == null) {
                throw new IllegalArgumentException("warningMarket must be specified when market warnings are included.");
            }
        }
    }
}
