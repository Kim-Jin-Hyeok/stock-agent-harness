package com.stock.market.stock.basicinfo.observation.analysis.restriction;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.Objects;

public class KisStockRestrictionAnalysisService {
    private final KisStockBasicInfoAnalysisService basicInfoAnalysisService;
    private final KisStockRestrictionScreeningPolicy restrictionScreeningPolicy;

    public KisStockRestrictionAnalysisService(
            KisStockBasicInfoAnalysisService basicInfoAnalysisService,
            KisStockRestrictionScreeningPolicy restrictionScreeningPolicy
    ) {
        this.basicInfoAnalysisService = Objects.requireNonNull(basicInfoAnalysisService, "basicInfoAnalysisService must not be null.");
        this.restrictionScreeningPolicy = Objects.requireNonNull(restrictionScreeningPolicy, "restrictionScreeningPolicy must not be null.");
    }

    public KisStockRestrictionAnalysisResult analyze(
            Long observationId,
            StockMasterBatchParseResult masterBatch,
            KisStockMarketWarningObservationResult marketWarningObservation
    ) {
        Objects.requireNonNull(observationId, "observationId must not be null.");
        if (observationId <= 0) {
            throw new IllegalArgumentException("observationId must be positive.");
        }
        Objects.requireNonNull(masterBatch, "masterBatch must not be null.");
        Objects.requireNonNull(marketWarningObservation, "marketWarningObservation must not be null.");
        var analysis = Objects.requireNonNull(basicInfoAnalysisService.analyze(observationId, masterBatch),
                "basicInfoAnalysis must not be null.");
        var matching = analysis.screeningResult().observation().typeResolution().matchingResult();
        if (!observationId.equals(analysis.observationId()) || !masterBatch.equals(matching.masterBatch())) {
            throw new IllegalStateException("Basic info analysis must preserve the requested observation ID and master batch.");
        }
        var screening = Objects.requireNonNull(restrictionScreeningPolicy.evaluate(analysis.screeningResult(), marketWarningObservation),
                "restrictionScreeningResult must not be null.");
        if (!marketWarningObservation.equals(screening.marketWarningObservation())) {
            throw new IllegalStateException("Restriction screening must preserve the supplied market warning observation.");
        }
        return new KisStockRestrictionAnalysisResult(analysis, screening);
    }
}
