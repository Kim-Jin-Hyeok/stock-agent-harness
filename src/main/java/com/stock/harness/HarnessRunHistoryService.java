package com.stock.harness;

import com.stock.harness.persistence.*;
import com.stock.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class HarnessRunHistoryService {
    private final HarnessRunSnapshotJsonConverter harnessRunSnapshotJsonConverter;
    private final HarnessRunRepository harnessRunRepository;
    private final HarnessStepRepository harnessStepRepository;

    private final List<HarnessRunResult> runs = new ArrayList<>();

    public void record(HarnessRunResult result) {
        runs.add(result);

        String decisionSnapshotJson = result.decision() == null
                ? null
                : harnessRunSnapshotJsonConverter.toDecisionJson(
                        HarnessDecisionSnapshot.from(result.decision())
                );

        String riskCheckSnapshotJson = result.riskCheckResult() == null
                ? null
                : harnessRunSnapshotJsonConverter.toRiskCheckJson(
                        HarnessRiskCheckSnapshot.from(result.riskCheckResult())
                );

        String portfolioSnapshotJson = result.portfolioSnapshot() == null
                ? null
                : harnessRunSnapshotJsonConverter.toPortfolioJson(
                        HarnessPortfolioSnapshot.from(result.portfolioSnapshot())
                );

        String marketSnapshotJson = result.marketSnapshot() == null
                ? null
                : harnessRunSnapshotJsonConverter.toMarketJson(
                        HarnessMarketSnapshot.from(result.marketSnapshot())
                );

        harnessRunRepository.save(HarnessRunEntity.from(
                result,
                decisionSnapshotJson,
                riskCheckSnapshotJson,
                portfolioSnapshotJson,
                marketSnapshotJson
        ));

        List<HarnessStepEntity> stepEntities = IntStream.range(0, result.steps().size())
                .mapToObj(index -> HarnessStepEntity.from(
                        result.runId(),
                        index + 1,
                        result.steps().get(index)
                ))
                .toList();
        harnessStepRepository.saveAll(stepEntities);
    }

    public Optional<HarnessRunResult> getRuntimeRunById(String runId) {
        return runs.stream()
                .filter(run -> run.runId().equals(runId))
                .findFirst();
    }

    public List<HarnessStepResult> getStepsByRunId(String runId) {
        return harnessStepRepository.findAllByRunIdOrderByStepOrderAsc(runId).stream()
                .map(HarnessStepEntity::toResult)
                .toList();
    }

    public boolean existsRun(String runId) {
        return harnessRunRepository.findByRunId(runId).isPresent();
    }

    public List<HarnessRunSummary> getRunSummaries() {
        return harnessRunRepository.findAllByOrderByStartedAtDesc().stream()
                .map(HarnessRunEntity::toSummary)
                .toList();
    }

    public Optional<HarnessRunEntity> findRunEntityById(String runId) {
        return harnessRunRepository.findByRunId(runId);
    }

    public Optional<HarnessRunDetail> getRunDetail(
            String runId,
            List<TradeRecord> tradeRecords
    ) {
        Optional<HarnessRunEntity> entityOptional = findRunEntityById(runId);

        if (entityOptional.isEmpty()) {
            return Optional.empty();
        }

        HarnessRunEntity entity = entityOptional.get();

        HarnessDecisionSnapshot decisionSnapshot = getDecisionSnapshot(entity);
        HarnessRiskCheckSnapshot riskCheckSnapshot = getRiskCheckSnapshot(entity);
        HarnessPortfolioSnapshot portfolioSnapshot = getPortfolioSnapshot(entity);
        HarnessMarketSnapshot marketSnapshot = getMarketSnapshot(entity);
        List<HarnessStepResult> steps = getStepsByRunId(runId);

        HarnessRunDetail detail = entity.toDetail(
                decisionSnapshot,
                riskCheckSnapshot,
                portfolioSnapshot,
                marketSnapshot,
                steps,
                tradeRecords
        );

        return Optional.of(detail);
    }

    public void clear() {
        runs.clear();
        harnessRunRepository.deleteAll();
        harnessStepRepository.deleteAll();
    }

    private HarnessDecisionSnapshot getDecisionSnapshot(HarnessRunEntity entity) {
        if (entity.getDecisionSnapshotJson() == null) {
            return null;
        }

        return harnessRunSnapshotJsonConverter.toDecisionSnapshot(
                entity.getDecisionSnapshotJson()
        );
    }

    private HarnessRiskCheckSnapshot getRiskCheckSnapshot(HarnessRunEntity entity) {
        if (entity.getRiskCheckSnapshotJson() == null) {
            return null;
        }

        return harnessRunSnapshotJsonConverter.toRiskCheckSnapshot(
                entity.getRiskCheckSnapshotJson()
        );
    }

    private HarnessPortfolioSnapshot getPortfolioSnapshot(HarnessRunEntity entity) {
        if (entity.getPortfolioSnapshotJson() == null) {
            return null;
        }

        return harnessRunSnapshotJsonConverter.toPortfolioSnapshot(
                entity.getPortfolioSnapshotJson()
        );
    }

    private HarnessMarketSnapshot getMarketSnapshot(HarnessRunEntity entity) {
        if (entity.getMarketSnapshotJson() == null) {
            return null;
        }

        return harnessRunSnapshotJsonConverter.toMarketSnapshot(
                entity.getMarketSnapshotJson()
        );
    }
}
