package com.stock.strategy.universe.candidate.evaluation.snapshot.support;

import com.stock.strategy.universe.candidate.evaluation.result.StockCandidateEvaluationResult;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;

import java.util.List;
import java.util.stream.Stream;

import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.eligible;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.history;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.input;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.request;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.service;

public final class StockCandidateEvaluationSnapshotFixture {
    private StockCandidateEvaluationSnapshotFixture() {}

    public static Stream<StockCandidateEvaluationSnapshot> snapshots() {
        return Stream.of(completeSnapshot(), eligibilityIncompleteSnapshot(), liquidityIncompleteSnapshot(),
                allIneligibleSnapshot(), belowMinimumSnapshot(), allMissingSnapshot());
    }

    public static StockCandidateEvaluationSnapshot completeSnapshot() {
        return StockCandidateEvaluationSnapshot.from(service().evaluate(request("005930", "000660", "069500"),
                List.of(eligible("005930"), eligible("000660"),
                        input("069500", StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED)),
                List.of(history("005930", 300L), history("000660", 100L), history("069500", 9000L))));
    }

    public static StockCandidateEvaluationSnapshot eligibilityIncompleteSnapshot() {
        return StockCandidateEvaluationSnapshot.from(service().evaluate(request("005930", "000660"),
                List.of(eligible("005930")), List.of(history("005930", 300L), history("000660", 100L))));
    }

    public static StockCandidateEvaluationSnapshot liquidityIncompleteSnapshot() {
        return StockCandidateEvaluationSnapshot.from(service().evaluate(request("005930", "000660"),
                List.of(eligible("005930"), eligible("000660")), List.of(history("005930", null, null))));
    }

    public static StockCandidateEvaluationSnapshot allIneligibleSnapshot() {
        return StockCandidateEvaluationSnapshot.from(service().evaluate(request("069500"),
                List.of(input("069500", StockSecurityType.ETF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED)),
                List.of(history("069500", null, null))));
    }

    public static StockCandidateEvaluationSnapshot belowMinimumSnapshot() {
        return StockCandidateEvaluationSnapshot.from(service().evaluate(request("005930"),
                List.of(eligible("005930")), List.of(history("005930", 0L))));
    }

    public static StockCandidateEvaluationSnapshot allMissingSnapshot() {
        return StockCandidateEvaluationSnapshot.from(service().evaluate(request("005930", "000660"),
                List.of(), List.of()));
    }

    public static StockCandidateEvaluationResult replay(StockCandidateEvaluationSnapshot snapshot) {
        StockCandidateEvaluationResult result = snapshot.evaluationResult();
        return service().evaluate(result.request(),
                result.eligibilityResults().stream().map(StockEligibilityResult::input).toList(), result.inputHistories());
    }
}
