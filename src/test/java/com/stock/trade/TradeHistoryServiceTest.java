package com.stock.trade;

import com.stock.agent.InvestmentAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class TradeHistoryServiceTest {

    @Test
    void recordStoresTradeResultAsTradeRecord() {
        TradeHistoryService tradeHistoryService = new TradeHistoryService();

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

        List<TradeRecord> records = tradeHistoryService.getRecords();

        assertThat(records).hasSize(1);

        TradeRecord record = records.getFirst();

        assertThat(record.runId()).isEqualTo("abc");
        assertThat(record.action()).isEqualTo(InvestmentAction.BUY);
        assertThat(record.symbol()).isEqualTo("TEST");
        assertThat(record.quantity()).isEqualTo(10L);
        assertThat(record.priceKrw()).isEqualTo(100_000L);
        assertThat(record.orderAmountKrw()).isEqualTo(1_000_000L);
        assertThat(record.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(record.reasonCode()).isEqualTo(TradeReasonCode.EXECUTION_COMPLETED);
        assertThat(record.executedAt()).isNotNull();
    }

    @Test
    void getRecordsByRunIdReturnsOnlyMatchingRecords() {
        TradeHistoryService tradeHistoryService = new TradeHistoryService();

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

        tradeHistoryService.record("run-1", result);
        tradeHistoryService.record("run-2", result);

        List<TradeRecord> records = tradeHistoryService.getRecordsByRunId("run-1");

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().runId()).isEqualTo("run-1");
    }
}