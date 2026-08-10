package com.stock.harness;

import java.util.ArrayList;
import java.util.List;

public class HarnessStepRecorder {

    private final List<HarnessStepResult> steps = new ArrayList<>();

    public void completed(HarnessStepType type, String message) {
        steps.add(new HarnessStepResult(type, HarnessStepStatus.COMPLETED, message));
    }

    public void failed(HarnessStepType type, String message) {
        steps.add(new HarnessStepResult(type, HarnessStepStatus.FAILED, message));
    }

    public void skipped(HarnessStepType type, String message) {
        steps.add(new HarnessStepResult(type, HarnessStepStatus.SKIPPED, message));
    }

    public List<HarnessStepResult> steps() {
        return List.copyOf(steps);
    }

    public int size() {
        return steps.size();
    }
}
