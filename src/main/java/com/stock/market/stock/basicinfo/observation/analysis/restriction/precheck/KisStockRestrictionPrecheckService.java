package com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.Objects;

public class KisStockRestrictionPrecheckService {
    private final KisStockRestrictionAnalysisService analysisService;
    private final KisStockRestrictionFreshnessPolicy freshnessPolicy;
    private final KisStockRestrictionPrecheckPolicy precheckPolicy;

    public KisStockRestrictionPrecheckService(
            KisStockRestrictionAnalysisService analysisService,
            KisStockRestrictionFreshnessPolicy freshnessPolicy,
            KisStockRestrictionPrecheckPolicy precheckPolicy
    ) {
        this.analysisService = Objects.requireNonNull(analysisService, "analysisService must not be null.");
        this.freshnessPolicy = Objects.requireNonNull(freshnessPolicy, "freshnessPolicy must not be null.");
        this.precheckPolicy = Objects.requireNonNull(precheckPolicy, "precheckPolicy must not be null.");
    }

    public KisStockRestrictionPrecheckResult precheck(
            Long observationId,
            StockMasterBatchParseResult masterBatch,
            KisStockMarketWarningObservationResult marketWarningObservation,
            KisStockRestrictionFreshnessRequest freshnessRequest
    ) {
        Objects.requireNonNull(observationId, "observationId must not be null.");
        if (observationId <= 0) {
            throw new IllegalArgumentException("observationId must be positive.");
        }
        Objects.requireNonNull(masterBatch, "masterBatch must not be null.");
        Objects.requireNonNull(marketWarningObservation, "marketWarningObservation must not be null.");
        Objects.requireNonNull(freshnessRequest, "freshnessRequest must not be null.");

        var analysis = Objects.requireNonNull(analysisService.analyze(observationId, masterBatch, marketWarningObservation),
                "Restriction analysis must not be null.");
        var matching = analysis.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult();
        if (!observationId.equals(analysis.basicInfoAnalysis().observationId()) || !masterBatch.equals(matching.masterBatch())
                || !marketWarningObservation.equals(analysis.restrictionScreeningResult().marketWarningObservation())) {
            throw new IllegalStateException("Restriction analysis must preserve the requested observation ID, master batch and market warning observation.");
        }

        var freshness = Objects.requireNonNull(freshnessPolicy.evaluate(freshnessRequest, analysis),
                "Restriction freshness must not be null.");
        if (!freshnessRequest.equals(freshness.request()) || !analysis.equals(freshness.analysisResult())) {
            throw new IllegalStateException("Restriction freshness must preserve the evaluation request and complete analysis result.");
        }

        var result = Objects.requireNonNull(precheckPolicy.evaluate(freshness), "Restriction precheck must not be null.");
        if (!freshness.equals(result.freshnessResult())) {
            throw new IllegalStateException("Restriction precheck must preserve the complete freshness result.");
        }
        return result;
    }
}
