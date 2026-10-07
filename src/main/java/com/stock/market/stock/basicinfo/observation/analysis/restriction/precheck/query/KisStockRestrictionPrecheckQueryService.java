package com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.query;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckService;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.result.KisStockMarketWarningObservationResult;

import java.util.Objects;
import java.util.Optional;

public class KisStockRestrictionPrecheckQueryService {
    private final KisStockBasicInfoObservationStore store;
    private final KisStockRestrictionPrecheckService precheckService;

    public KisStockRestrictionPrecheckQueryService(
            KisStockBasicInfoObservationStore store,
            KisStockRestrictionPrecheckService precheckService
    ) {
        this.store = Objects.requireNonNull(store, "store must not be null.");
        this.precheckService = Objects.requireNonNull(precheckService, "precheckService must not be null.");
    }

    public Optional<KisStockRestrictionPrecheckResult> precheckLatest(
            String symbol,
            StockMasterBatchParseResult masterBatch,
            KisStockMarketWarningObservationResult marketWarningObservation,
            KisStockRestrictionFreshnessRequest freshnessRequest
    ) {
        if (symbol == null || !symbol.matches("[0-9A-Z]{6}")) {
            throw new IllegalArgumentException("symbol must be exactly 6 uppercase alphanumeric characters.");
        }
        Objects.requireNonNull(masterBatch, "masterBatch must not be null.");
        Objects.requireNonNull(marketWarningObservation, "marketWarningObservation must not be null.");
        Objects.requireNonNull(freshnessRequest, "freshnessRequest must not be null.");

        var selectedId = Objects.requireNonNull(store.findLatestObservationId(symbol, freshnessRequest.evaluatedAt()),
                "Stored observation ID selection must not be null.");
        if (selectedId.isEmpty()) {
            return Optional.empty();
        }
        Long observationId = selectedId.orElseThrow();
        if (observationId <= 0) {
            throw new IllegalStateException("Selected observation ID must be positive.");
        }

        var result = Objects.requireNonNull(precheckService.precheck(
                observationId, masterBatch, marketWarningObservation, freshnessRequest), "Restriction precheck must not be null.");
        var freshness = result.freshnessResult();
        var analysis = freshness.analysisResult();
        var basic = analysis.basicInfoAnalysis();
        var matching = basic.screeningResult().observation().typeResolution().matchingResult();
        if (!observationId.equals(basic.observationId()) || !symbol.equals(basic.response().requestedSymbol())
                || !freshnessRequest.equals(freshness.request()) || !masterBatch.equals(matching.masterBatch())
                || !marketWarningObservation.equals(analysis.restrictionScreeningResult().marketWarningObservation())) {
            throw new IllegalStateException("Restriction precheck must preserve the selected observation ID, requested symbol, master batch, market warning observation and freshness request.");
        }
        return Optional.of(result);
    }
}
