package com.stock.harness;

import com.stock.harness.persistence.HarnessRunEntity;
import com.stock.harness.persistence.HarnessRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HarnessRunHistoryService {
    private final HarnessRunRepository harnessRunRepository;

    private final List<HarnessRunResult> runs = new ArrayList<>();

    public void record(HarnessRunResult result) {
        runs.add(result);
        harnessRunRepository.save(HarnessRunEntity.from(result));
    }

    public List<HarnessRunSummary> getRuns() {
        return getRunSummaries();
    }

    public Optional<HarnessRunResult> getRunById(String runId) {
        return runs.stream()
                .filter(run -> run.runId().equals(runId))
                .findFirst();
    }

    public List<HarnessRunSummary> getRunSummaries() {
        return runs.stream()
                .map(result -> new HarnessRunSummary(
                        result.runId(),
                        result.status(),
                        result.startedAt(),
                        result.finishedAt()
                ))
                .toList();
    }

    public void clear() {
        runs.clear();
    }
}
