package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@ConfigurationProperties(prefix = "market.stock.basic-info.analysis.manual")
public record KisStockBasicInfoAnalysisProperties(
        boolean enabled,
        Long observationId,
        String observationRoot,
        UUID collectionId,
        boolean includeMarketWarnings,
        KisStockMasterMarket warningMarket,
        boolean checkFreshness,
        Instant evaluatedAt,
        Duration maxMasterAge,
        Duration maxBasicInfoAge
) {
    public KisStockBasicInfoAnalysisProperties(boolean enabled, Long observationId, String observationRoot, UUID collectionId) {
        this(enabled, observationId, observationRoot, collectionId, false, null);
    }

    public KisStockBasicInfoAnalysisProperties(boolean enabled, Long observationId, String observationRoot, UUID collectionId,
                                               boolean includeMarketWarnings, KisStockMasterMarket warningMarket) {
        this(enabled, observationId, observationRoot, collectionId, includeMarketWarnings, warningMarket, false, null, null, null);
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
            if (checkFreshness) {
                if (!includeMarketWarnings) {
                    throw new IllegalArgumentException("includeMarketWarnings must be enabled when freshness is checked.");
                }
                new KisStockRestrictionFreshnessRequest(evaluatedAt, maxMasterAge, maxBasicInfoAge);
            }
        }
    }
}
