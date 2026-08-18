package com.stock.harness.api;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.HarnessRunSummary;
import com.stock.harness.HarnessStateService;
import com.stock.harness.HarnessStepResult;
import com.stock.harness.HarnessStepStatus;
import com.stock.harness.HarnessStepType;
import com.stock.harness.InvestmentHarness;
import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeRecord;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HarnessController.class)
class HarnessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestmentHarness investmentHarness;

    @MockitoBean
    private TradeHistoryService tradeHistoryService;

    @MockitoBean
    private HarnessRunHistoryService harnessRunHistoryService;

    @MockitoBean
    private HarnessStateService harnessStateService;

    @Test
    void runReturnsHarnessRunResponseWithTradeRecords() throws Exception {
        String runId = "run-id";

        when(investmentHarness.run())
                .thenReturn(completedRun(runId));
        when(tradeHistoryService.getRecordsByRunId(runId))
                .thenReturn(List.of(executedBuyTradeRecord(runId)));

        mockMvc.perform(post("/api/harness/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.tradeRecords[0].runId").value(runId))
                .andExpect(jsonPath("$.tradeRecords[0].status").value("EXECUTED"));

        verify(investmentHarness).run();
        verify(tradeHistoryService).getRecordsByRunId(runId);
    }

    @Test
    void getRunsWithoutTradeRecords() throws Exception {
        String runId = "run-1";

        when(harnessRunHistoryService.getRunSummaries())
                .thenReturn(List.of(completedSummary(runId)));

        mockMvc.perform(get("/api/harness/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        verify(harnessRunHistoryService).getRunSummaries();
        verify(harnessRunHistoryService, never()).getRuns();
        verify(tradeHistoryService, never()).getRecordsByRunId(any());
    }

    @Test
    void getRunReturnsRunResultWithTradeRecords() throws Exception {
        String runId = "run-1";

        when(harnessRunHistoryService.getRunById(runId))
                .thenReturn(Optional.of(completedRun(runId)));
        when(tradeHistoryService.getRecordsByRunId(runId))
                .thenReturn(List.of(executedBuyTradeRecord(runId)));

        mockMvc.perform(get("/api/harness/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decision.action").value("BUY"))
                .andExpect(jsonPath("$.tradeRecords[0].runId").value(runId))
                .andExpect(jsonPath("$.tradeRecords[0].status").value("EXECUTED"))
                .andExpect(jsonPath("$.tradeRecords[0].reasonCode").value("EXECUTION_COMPLETED"));
    }

    @Test
    void getRunReturnsNotFoundWhenRunDoesNotExist() throws Exception {
        String runId = "missing-run";

        when(harnessRunHistoryService.getRunById(runId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/harness/runs/{runId}", runId))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetDelegatesToHarnessStateService() throws Exception {
        mockMvc.perform(post("/api/harness/reset"))
                .andExpect(status().isOk());

        verify(harnessStateService).reset();
    }

    private HarnessRunResult completedRun(String runId) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt,
                List.of(completedStep()),
                buyDecision(),
                approvedRiskCheckResult(),
                executedBuyTradeResult(),
                portfolioSnapshot(),
                marketSnapshot()
        );
    }

    private HarnessRunSummary completedSummary(String runId) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return new HarnessRunSummary(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt
        );
    }

    private HarnessStepResult completedStep() {
        return new HarnessStepResult(
                HarnessStepType.EXECUTE_TRADE,
                HarnessStepStatus.COMPLETED,
                "Trade execution completed."
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                "Buy Samsung Electronics."
        );
    }

    private RiskCheckResult approvedRiskCheckResult() {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                RiskReasonCode.RISK_APPROVED,
                "Risk check approved."
        );
    }

    private TradeResult executedBuyTradeResult() {
        return new TradeResult(
                TradeStatus.EXECUTED,
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                700_000L,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution completed."
        );
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

    private PortfolioSnapshot portfolioSnapshot() {
        return new PortfolioSnapshot(
                9_300_000L,
                10_000_000L,
                List.of()
        );
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }
}
