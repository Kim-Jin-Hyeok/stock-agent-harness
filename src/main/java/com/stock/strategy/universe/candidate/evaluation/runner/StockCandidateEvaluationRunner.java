package com.stock.strategy.universe.candidate.evaluation.runner;

import com.stock.strategy.universe.candidate.evaluation.query.StockCandidateEvaluationQueryService;
import com.stock.strategy.universe.candidate.evaluation.runner.config.StockCandidateEvaluationProperties;
import com.stock.strategy.universe.candidate.evaluation.snapshot.StockCandidateEvaluationSnapshot;
import com.stock.strategy.universe.candidate.evaluation.snapshot.storage.StockCandidateEvaluationSnapshotStore;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "strategy.universe.candidate.evaluation.manual",
        name = "enabled",
        havingValue = "true"
)
public class StockCandidateEvaluationRunner implements ApplicationRunner {
    private final StockCandidateEvaluationQueryService queryService;
    private final StockCandidateEvaluationSnapshotStore snapshotStore;
    private final StockCandidateEvaluationProperties properties;

    public StockCandidateEvaluationRunner(
            StockCandidateEvaluationQueryService queryService,
            StockCandidateEvaluationSnapshotStore snapshotStore,
            StockCandidateEvaluationProperties properties
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null.");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore must not be null.");
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }
        var request = properties.toRequest();
        log.info("Stock candidate evaluation started. request={}", request);
        var result = Objects.requireNonNull(
                queryService.evaluate(request, properties.eligibilityInputs()), "evaluationResult must not be null."
        );
        if (!request.equals(result.request())) {
            throw new IllegalStateException("Evaluation result request must match manual evaluation request.");
        }

        var snapshot = StockCandidateEvaluationSnapshot.from(result);
        Long snapshotId = Objects.requireNonNull(snapshotStore.save(snapshot), "snapshotId must not be null.");
        if (snapshotId <= 0) {
            throw new IllegalStateException("Saved snapshotId must be positive.");
        }
        long eligibleCount = result.eligibilityResults().stream()
                .filter(value -> value.status() == StockEligibilityStatus.ELIGIBLE).count();
        long excludedCount = result.eligibilityResults().stream()
                .filter(value -> value.status() == StockEligibilityStatus.INELIGIBLE).count();
        long eligibilityUnverifiedCount = result.eligibilityResults().stream()
                .filter(value -> value.status() == StockEligibilityStatus.DATA_UNVERIFIED).count();
        boolean liquidityEvaluated = result.liquidityResult() != null;
        int liquidityUnverifiedCount = liquidityEvaluated ? result.liquidityResult().unverifiedSymbols().size() : 0;
        log.info(
                "Stock candidate evaluation recorded. snapshotId={}, status={}, selectionAsOfDate={}, targetCount={}, "
                        + "eligibleCount={}, excludedCount={}, eligibilityUnverifiedCount={}, liquidityEvaluated={}, "
                        + "liquidityUnverifiedCount={}, selectedCount={}",
                snapshotId, result.status(), request.eligibilityRequest().selectionAsOfDate(),
                request.targetSymbols().size(), eligibleCount, excludedCount, eligibilityUnverifiedCount,
                liquidityEvaluated, liquidityUnverifiedCount, result.candidateSymbols().size()
        );
    }
}
