package com.stock.strategy.universe.eligibility.restriction.kis.freshness.result;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public enum KisStockRestrictionFreshnessReasonCode {
    MASTER_OBSERVATION_MARKET_UNVERIFIED(true),
    MASTER_OBSERVATION_SOURCE_UNVERIFIED(true),
    MASTER_OBSERVATION_AFTER_EVALUATION(true),
    MASTER_OBSERVATION_EXPIRED(false),
    BASIC_INFO_OBSERVATION_AFTER_EVALUATION(true),
    BASIC_INFO_OBSERVATION_EXPIRED(false);

    private final boolean timeUnverified;

    KisStockRestrictionFreshnessReasonCode(boolean timeUnverified) {
        this.timeUnverified = timeUnverified;
    }

    public boolean isTimeUnverified() {
        return timeUnverified;
    }

    public static List<KisStockRestrictionFreshnessReasonCode> fromInputs(
            KisStockRestrictionFreshnessRequest request,
            KisStockRestrictionAnalysisResult analysisResult
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(analysisResult, "analysisResult must not be null.");
        var reasons = new ArrayList<KisStockRestrictionFreshnessReasonCode>();
        var matching = analysisResult.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult();
        var master = matching.masterBatch();
        var market = matching.comparedMarket();
        if (market == null) {
            reasons.add(MASTER_OBSERVATION_MARKET_UNVERIFIED);
        } else {
            var source = master.marketResults().stream().filter(result -> result.market() == market).findFirst().orElseThrow();
            var warningSource = analysisResult.restrictionScreeningResult().marketWarningObservation().source().source();
            // Collection timestamps cannot be attributed to a different warning source, even if its claimed hash matches.
            if (!source.equals(warningSource)) {
                reasons.add(MASTER_OBSERVATION_SOURCE_UNVERIFIED);
            } else {
                var file = master.collection().files().stream().filter(observation -> observation.market() == market)
                        .findFirst().orElseThrow();
                // The whole batch must already be collected; only the selected file determines its age.
                observe(reasons, file.startedAt(), master.collection().finishedAt(), request.evaluatedAt(), request.maxMasterAge(),
                        MASTER_OBSERVATION_AFTER_EVALUATION, MASTER_OBSERVATION_EXPIRED);
            }
        }
        var response = analysisResult.basicInfoAnalysis().response();
        observe(reasons, response.requestStartedAt(), response.responseReceivedAt(), request.evaluatedAt(), request.maxBasicInfoAge(),
                BASIC_INFO_OBSERVATION_AFTER_EVALUATION, BASIC_INFO_OBSERVATION_EXPIRED);
        return List.copyOf(reasons);
    }

    private static void observe(List<KisStockRestrictionFreshnessReasonCode> reasons,
                                Instant startedAt, Instant finishedAt, Instant evaluatedAt, Duration maxAge,
                                KisStockRestrictionFreshnessReasonCode afterEvaluation,
                                KisStockRestrictionFreshnessReasonCode expired) {
        if (finishedAt.isAfter(evaluatedAt)) {
            reasons.add(afterEvaluation);
        } else if (Duration.between(startedAt, evaluatedAt).compareTo(maxAge) >= 0) {
            reasons.add(expired);
        }
    }
}
