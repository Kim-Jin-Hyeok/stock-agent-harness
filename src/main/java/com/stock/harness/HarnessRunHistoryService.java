package com.stock.harness;

import com.stock.harness.persistence.HarnessDecisionSnapshot;
import com.stock.harness.persistence.HarnessRiskCheckSnapshot;
import com.stock.harness.persistence.HarnessRunEntity;
import com.stock.harness.persistence.HarnessRunRepository;
import com.stock.harness.persistence.HarnessRunSnapshotJsonConverter;
import com.stock.harness.persistence.HarnessStepEntity;
import com.stock.harness.persistence.HarnessStepRepository;
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

        harnessRunRepository.save(HarnessRunEntity.from(
                result,
                decisionSnapshotJson,
                riskCheckSnapshotJson
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

    public Optional<HarnessRunResult> getRunById(String runId) {
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

    public void clear() {
        runs.clear();
        harnessRunRepository.deleteAll();
        harnessStepRepository.deleteAll();
    }
}
