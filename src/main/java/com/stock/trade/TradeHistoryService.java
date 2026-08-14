package com.stock.trade;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TradeHistoryService {
    private final List<TradeRecord> records = new ArrayList<>();

    public void record(String runId, TradeResult result) {
        LocalDateTime executedAt = LocalDateTime.now();

        records.add(
                new TradeRecord(
                        runId,
                        result.action(),
                        result.symbol(),
                        result.quantity(),
                        result.expectedPriceKrw(),
                        result.estimatedOrderAmountKrw(),
                        result.status(),
                        result.reasonCode(),
                        result.reason(),
                        executedAt
                )
        );
    }

    public List<TradeRecord> getRecords() {
        return List.copyOf(records);
    }

    public List<TradeRecord> getRecordsByRunId(String runId) {
        return records.stream()
                .filter(record -> record.runId().equals(runId))
                .toList();
    }
}
