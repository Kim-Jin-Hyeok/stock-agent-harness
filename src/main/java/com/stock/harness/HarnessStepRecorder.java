package com.stock.harness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class HarnessStepRecorder {

    public <T> T record(
            HarnessStepType type,
            Supplier<T> action,
            String successMessage
    ) {
        LocalDateTime startedAt = LocalDateTime.now();

        try {
            T result = action.get();
            LocalDateTime finishedAt = LocalDateTime.now();

            steps.add(new HarnessStepResult(
                    type,
                    HarnessStepStatus.COMPLETED,
                    successMessage,
                    startedAt,
                    finishedAt
            ));

            return result;
        } catch (RuntimeException e) {
            LocalDateTime finishedAt = LocalDateTime.now();

            steps.add(new HarnessStepResult(
                    type,
                    HarnessStepStatus.FAILED,
                    e.getMessage(),
                    startedAt,
                    finishedAt
            ));

            throw e;
        }
    }

    public <T> T record(
            HarnessStepType type,
            Supplier<T> action,
            Function<T, String> successMessageResolver
    ) {
        LocalDateTime startedAt = LocalDateTime.now();

        try {
            T result = action.get();
            LocalDateTime finishedAt = LocalDateTime.now();

            steps.add(new HarnessStepResult(
                    type,
                    HarnessStepStatus.COMPLETED,
                    successMessageResolver.apply(result),
                    startedAt,
                    finishedAt
            ));

            return result;
        } catch (RuntimeException e) {
            LocalDateTime finishedAt = LocalDateTime.now();

            steps.add(new HarnessStepResult(
                    type,
                    HarnessStepStatus.FAILED,
                    e.getMessage(),
                    startedAt,
                    finishedAt
            ));

            throw e;
        }
    }

    public <T> T record(
            HarnessStepType type,
            Supplier<T> action,
            Function<T, HarnessStepStatus> statusResolver,
            Function<T, String> messageResolver
    ) {
        LocalDateTime startedAt = LocalDateTime.now();

        try {
            T result = action.get();
            LocalDateTime finishedAt = LocalDateTime.now();

            steps.add(new HarnessStepResult(
                    type,
                    statusResolver.apply(result),
                    messageResolver.apply(result),
                    startedAt,
                    finishedAt
            ));

            return result;
        } catch (RuntimeException e) {
            LocalDateTime finishedAt = LocalDateTime.now();

            steps.add(new HarnessStepResult(
                    type,
                    HarnessStepStatus.FAILED,
                    e.getMessage(),
                    startedAt,
                    finishedAt
            ));

            throw e;
        }
    }

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
