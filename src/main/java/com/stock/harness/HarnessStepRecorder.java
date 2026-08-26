package com.stock.harness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HarnessStepRecorder {

    private final List<HarnessStepResult> steps = new ArrayList<>();

    public void completed(HarnessStepType type, String message) {
        record(type, HarnessStepStatus.COMPLETED, message);
    }

    public void failed(HarnessStepType type, String message) {
        record(type, HarnessStepStatus.FAILED, message);
    }

    public void skipped(HarnessStepType type, String message) {
        record(type, HarnessStepStatus.SKIPPED, message);
    }

    private void record(
            HarnessStepType type,
            HarnessStepStatus status,
            String message
    ) {
        LocalDateTime recordedAt = LocalDateTime.now();

        steps.add(new HarnessStepResult(
                type,
                status,
                message,
                recordedAt,
                recordedAt
        ));
    }

    public List<HarnessStepResult> steps() {
        return List.copyOf(steps);
    }

    public int size() {
        return steps.size();
    }
}
