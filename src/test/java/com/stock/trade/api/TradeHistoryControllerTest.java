package com.stock.trade.api;

import com.stock.agent.InvestmentAction;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeRecord;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeHistoryController.class)
class TradeHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeHistoryService tradeHistoryService;

    @Test
    void getTradesReturnRecordsWhenRunIdMissing() throws Exception {
        when(tradeHistoryService.getRecords())
                .thenReturn(tradeRecords());

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value("run-1"))
                .andExpect(jsonPath("$[1].runId").value("run-2"));

        verify(tradeHistoryService).getRecords();
        verify(tradeHistoryService, never()).getRecordsByRunId(any());
    }

    @Test
    void getTradesReturnRecordWhenRunIdExist() throws Exception {
        String runId = "run-1";

        when(tradeHistoryService.getRecordsByRunId(runId))
                .thenReturn(runTradeRecords(runId));

        mockMvc.perform(get("/api/trades")
                .param("runId", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId));

        verify(tradeHistoryService).getRecordsByRunId(runId);
        verify(tradeHistoryService, never()).getRecords();
    }

    private List<TradeRecord> tradeRecords() {
        return List.of(
                executedBuyTradeRecord("run-1"),
                skippedHoldTradeRecord("run-2")
        );
    }

    private List<TradeRecord> runTradeRecords(String runId) {
        return List.of(executedBuyTradeRecord(runId));
    }

    private TradeRecord executedBuyTradeRecord(String runId) {
        return new TradeRecord(
                runId,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                TradeStatus.EXECUTED,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution completed.",
                LocalDateTime.of(2026, 1, 1, 9, 0, 1)
        );
    }

    private TradeRecord skippedHoldTradeRecord(String runId) {
        return new TradeRecord(
                runId,
                InvestmentAction.HOLD,
                null,
                null,
                null,
                0L,
                TradeStatus.SKIPPED,
                TradeReasonCode.HOLD_NO_ORDER,
                "HOLD decision does not require order execution.",
                LocalDateTime.of(2026, 1, 1, 9, 10, 1)
        );
    }
}
