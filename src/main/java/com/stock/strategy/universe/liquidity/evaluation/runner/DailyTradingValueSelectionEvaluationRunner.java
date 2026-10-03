package com.stock.strategy.universe.liquidity.evaluation.runner;

import com.stock.strategy.universe.liquidity.evaluation.query.DailyTradingValueSelectionQueryService;
import com.stock.strategy.universe.liquidity.evaluation.runner.config.DailyTradingValueSelectionEvaluationProperties;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.DailyTradingValueSelectionSnapshot;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.DailyTradingValueSelectionSnapshotStore;
import com.stock.strategy.universe.liquidity.selection.result.DailyTradingValueSelectionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "strategy.universe.liquidity.evaluation.manual",
        name = "enabled",
        havingValue = "true"
)
public class DailyTradingValueSelectionEvaluationRunner implements ApplicationRunner {
    private final DailyTradingValueSelectionQueryService queryService;
    private final DailyTradingValueSelectionSnapshotStore snapshotStore;
    private final DailyTradingValueSelectionEvaluationProperties properties;

    public DailyTradingValueSelectionEvaluationRunner(
            DailyTradingValueSelectionQueryService queryService,
            DailyTradingValueSelectionSnapshotStore snapshotStore,
            DailyTradingValueSelectionEvaluationProperties properties
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
        log.info("Daily trading value selection evaluation started. request={}", request);
        var result = Objects.requireNonNull(queryService.evaluate(request), "evaluationResult must not be null.");
        if (!request.equals(result.request())) {
            throw new IllegalStateException("Evaluation result request must match manual evaluation request.");
        }

        var snapshot = DailyTradingValueSelectionSnapshot.from(result);
        Long snapshotId = Objects.requireNonNull(snapshotStore.save(snapshot), "snapshotId must not be null.");
        if (snapshotId <= 0) {
            throw new IllegalStateException("Saved snapshotId must be positive.");
        }
        long selectedCount = result.selectionResults().stream()
                .filter(selection -> selection.status() == DailyTradingValueSelectionStatus.SELECTED).count();
        log.info(
                "Daily trading value selection evaluation recorded. snapshotId={}, status={}, selectionAsOfDate={}, "
                        + "targetCount={}, calculatedCount={}, selectedCount={}, unverifiedCount={}",
                snapshotId, result.status(), request.selectionAsOfDate(), request.targetSymbols().size(),
                result.calculatedAverages().size(), selectedCount, result.unverifiedSymbols().size()
        );
    }
}
