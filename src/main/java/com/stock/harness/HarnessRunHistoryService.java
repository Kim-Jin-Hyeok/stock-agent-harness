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

    public List<HarnessRunResult> getRuns() {
        return List.copyOf(runs);
    }

    public Optional<HarnessRunResult> getRunById(String runId) {
        return runs.stream()
                .filter(run -> run.runId().equals(runId))
                .findFirst();
    }

    public void clear() {
        runs.clear();
    }
}
