package com.stock.trade;

import com.stock.trade.persistence.TradeRecordEntity;
import com.stock.trade.persistence.TradeRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeHistoryService {
    private final TradeRecordRepository tradeRecordRepository;

    public void record(String runId, TradeResult result) {
        LocalDateTime executedAt = LocalDateTime.now();

        tradeRecordRepository.save(TradeRecordEntity.from(runId, result, executedAt));
    }

    public List<TradeRecord> getRecords() {
        return tradeRecordRepository.findAllByOrderByExecutedAtDesc().stream()
                .map(TradeRecordEntity::toRecord)
                .toList();
    }

    public List<TradeRecord> getRecordsByRunId(String runId) {
        return tradeRecordRepository.findAllByRunIdOrderByExecutedAtDesc(runId).stream()
                .map(TradeRecordEntity::toRecord)
                .toList();
    }

    public void clear() {
        tradeRecordRepository.deleteAll();
    }
}
