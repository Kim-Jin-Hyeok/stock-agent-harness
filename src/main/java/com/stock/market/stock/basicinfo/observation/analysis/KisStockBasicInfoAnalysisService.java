package com.stock.market.stock.basicinfo.observation.analysis;

import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.KisStockBasicInfoTypeResolutionPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;

import java.util.NoSuchElementException;
import java.util.Objects;

public class KisStockBasicInfoAnalysisService {
    private final KisStockBasicInfoObservationStore store;
    private final KisStockBasicInfoParser parser;
    private final KisStockBasicInfoMatchingPolicy matchingPolicy;
    private final KisStockBasicInfoTypeResolutionPolicy typeResolutionPolicy;
    private final KisStockBasicInfoRestrictionObservationPolicy restrictionObservationPolicy;
    private final KisStockBasicInfoRestrictionScreeningPolicy restrictionScreeningPolicy;

    public KisStockBasicInfoAnalysisService(
            KisStockBasicInfoObservationStore store,
            KisStockBasicInfoParser parser,
            KisStockBasicInfoMatchingPolicy matchingPolicy,
            KisStockBasicInfoTypeResolutionPolicy typeResolutionPolicy,
            KisStockBasicInfoRestrictionObservationPolicy restrictionObservationPolicy,
            KisStockBasicInfoRestrictionScreeningPolicy restrictionScreeningPolicy
    ) {
        this.store = Objects.requireNonNull(store, "store must not be null.");
        this.parser = Objects.requireNonNull(parser, "parser must not be null.");
        this.matchingPolicy = Objects.requireNonNull(matchingPolicy, "matchingPolicy must not be null.");
        this.typeResolutionPolicy = Objects.requireNonNull(typeResolutionPolicy, "typeResolutionPolicy must not be null.");
        this.restrictionObservationPolicy = Objects.requireNonNull(restrictionObservationPolicy,
                "restrictionObservationPolicy must not be null.");
        this.restrictionScreeningPolicy = Objects.requireNonNull(restrictionScreeningPolicy,
                "restrictionScreeningPolicy must not be null.");
    }

    public KisStockBasicInfoAnalysisResult analyze(Long observationId, StockMasterBatchParseResult masterBatch) {
        Objects.requireNonNull(observationId, "observationId must not be null.");
        if (observationId <= 0) {
            throw new IllegalArgumentException("observationId must be positive.");
        }
        Objects.requireNonNull(masterBatch, "masterBatch must not be null.");
        var response = store.findById(observationId).orElseThrow(
                () -> new NoSuchElementException("KIS stock basic info observation not found. id=" + observationId));
        var parsed = parser.parse(response.content());
        // Matching uses the stored request, never the API product number or another caller-supplied symbol.
        var matching = matchingPolicy.match(masterBatch, response.requestedSymbol(), parsed);
        var typeResolution = typeResolutionPolicy.resolve(matching);
        var restrictionObservation = restrictionObservationPolicy.evaluate(typeResolution);
        var screening = restrictionScreeningPolicy.evaluate(restrictionObservation);
        return new KisStockBasicInfoAnalysisResult(observationId, response, screening);
    }
}
