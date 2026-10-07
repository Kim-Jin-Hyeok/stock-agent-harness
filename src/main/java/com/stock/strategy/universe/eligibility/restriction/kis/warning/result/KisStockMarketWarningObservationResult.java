package com.stock.strategy.universe.eligibility.restriction.kis.warning.result;

import com.stock.market.stock.master.provider.kis.parsing.warning.result.KisStockMasterMarketWarningParseResult;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;

import java.util.List;
import java.util.Objects;

public record KisStockMarketWarningObservationResult(
        KisStockMasterMarketWarningParseResult source,
        List<KisStockMarketWarningObservation> observations,
        String observationVersion,
        String sourceRevision
) {
    public KisStockMarketWarningObservationResult {
        KisStockMarketWarningObservationPolicy.requireSupportedSource(source);
        observations = List.copyOf(Objects.requireNonNull(observations, "observations must not be null."));
        if (!KisStockMarketWarningObservationPolicy.OBSERVATION_VERSION.equals(observationVersion)) {
            throw new IllegalArgumentException("observationVersion must be the supported market warning observation version.");
        }
        if (!KisStockMarketWarningObservationPolicy.SOURCE_REVISION.equals(sourceRevision)) {
            throw new IllegalArgumentException("sourceRevision must be the verified market warning observation revision.");
        }
        if (observations.size() != source.records().size()) {
            throw new IllegalArgumentException("Market warning observations must contain every source row exactly once.");
        }
        for (int index = 0; index < observations.size(); index++) {
            if (!observations.get(index).rawRecord().equals(source.records().get(index))) {
                throw new IllegalArgumentException("Market warning observations must preserve original raw fields, identifiers and row order.");
            }
        }
    }
}
