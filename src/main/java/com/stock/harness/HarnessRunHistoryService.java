package com.stock.harness;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class HarnessRunHistoryService {
    private final List<HarnessRunResult> runs = new ArrayList<>();

    public void record(HarnessRunResult result) {
        runs.add(result);
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
