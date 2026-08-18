package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.trade.persistence.TradeRecordEntity;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeHistoryServiceTest {

    @Test
    void recordStoresTradeResultAsTradeRecord() {
        TradeRecordRepository tradeRecordRepository = mock(TradeRecordRepository.class);
        TradeHistoryService tradeHistoryService = new TradeHistoryService(tradeRecordRepository);

        TradeResult result = new TradeResult(
                TradeStatus.EXECUTED,
                InvestmentAction.BUY,
                "TEST",
                10L,
                100_000L,
                1_000_000L,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution is complete."
        );

        tradeHistoryService.record("abc", result);

        verify(tradeRecordRepository).save(any());
    }

    @Test
    void getRecordsReturnAllRecords() {
        TradeRecordRepository tradeRecordRepository = mock(TradeRecordRepository.class);
        TradeHistoryService tradeHistoryService = new TradeHistoryService(tradeRecordRepository);

        when(tradeRecordRepository.findAllByOrderByExecutedAtDesc())
                .thenReturn(List.of(
                        executedBuyTradeRecordEntity("run-2", LocalDateTime.of(2026, 1, 1, 9, 10)),
                        executedBuyTradeRecordEntity("run-1", LocalDateTime.of(2026, 1, 1, 9, 0))
                ));

        List<TradeRecord> records = tradeHistoryService.getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().runId()).isEqualTo("run-2");
        assertThat(records.getLast().runId()).isEqualTo("run-1");

        verify(tradeRecordRepository).findAllByOrderByExecutedAtDesc();
    }

    @Test
    void getRecordsByRunIdReturnsOnlyMatchingRecords() {
        TradeRecordRepository tradeRecordRepository = mock(TradeRecordRepository.class);
        TradeHistoryService tradeHistoryService = new TradeHistoryService(tradeRecordRepository);

        when(tradeRecordRepository.findAllByRunIdOrderByExecutedAtDesc("run-1"))
                .thenReturn(List.of(executedBuyTradeRecordEntity("run-1")));

        List<TradeRecord> records = tradeHistoryService.getRecordsByRunId("run-1");

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().runId()).isEqualTo("run-1");

        verify(tradeRecordRepository).findAllByRunIdOrderByExecutedAtDesc("run-1");
    }

    @Test
    void clearTradeHistoryWhenCalledClear() {
        TradeRecordRepository tradeRecordRepository = mock(TradeRecordRepository.class);
        TradeHistoryService tradeHistoryService = new TradeHistoryService(tradeRecordRepository);

        tradeHistoryService.clear();

        verify(tradeRecordRepository).deleteAll();
    }

    private TradeRecordEntity executedBuyTradeRecordEntity(String runId, LocalDateTime executedAt) {
        return TradeRecordEntity.of(
                runId,
                InvestmentAction.BUY,
                "TEST",
                10L,
                100_000L,
                1_000_000L,
                TradeStatus.EXECUTED,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution is complete.",
                executedAt
        );
    }

    private TradeRecordEntity executedBuyTradeRecordEntity(String runId) {
        return TradeRecordEntity.of(
                runId,
                InvestmentAction.BUY,
                "TEST",
                10L,
                100_000L,
                1_000_000L,
                TradeStatus.EXECUTED,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution is complete.",
                LocalDateTime.of(2026, 1, 1, 9, 0)
        );
    }
}
