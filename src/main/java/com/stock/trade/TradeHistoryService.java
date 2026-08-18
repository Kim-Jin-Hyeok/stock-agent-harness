package com.stock.trade;

import com.stock.trade.persistence.TradeRecordEntity;
import com.stock.trade.persistence.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeHistoryService {
    private final TradeRecordRepository tradeRecordRepository;
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

        tradeRecordRepository.save(TradeRecordEntity.from(runId, result, executedAt));
    }

    public List<TradeRecord> getRecords() {
        return List.copyOf(records);
    }

    public List<TradeRecord> getRecordsByRunId(String runId) {
        return records.stream()
                .filter(record -> record.runId().equals(runId))
                .toList();
    }

    public void clear() {
        records.clear();
        tradeRecordRepository.deleteAll();
    }
}
