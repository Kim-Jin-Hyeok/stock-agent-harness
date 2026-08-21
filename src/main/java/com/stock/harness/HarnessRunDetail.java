package com.stock.harness;

import com.stock.trade.TradeRecord;

import java.time.LocalDateTime;
import java.util.List;

public record HarnessRunDetail(
        String runId,
        HarnessRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        List<HarnessStepResult> steps,
        List<TradeRecord> tradeRecords
) {
}
