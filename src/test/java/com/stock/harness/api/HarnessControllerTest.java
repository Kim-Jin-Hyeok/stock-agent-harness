package com.stock.harness.api;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.agent.evidence.movingaverage.MovingAverageDecisionEvidence;
import com.stock.harness.HarnessRunDetail;
import com.stock.harness.HarnessRunHistoryService;
import com.stock.harness.HarnessRunResult;
import com.stock.harness.HarnessRunStatus;
import com.stock.harness.HarnessRunSummary;
import com.stock.harness.HarnessStateService;
import com.stock.harness.HarnessStepResult;
import com.stock.harness.HarnessStepStatus;
import com.stock.harness.HarnessStepType;
import com.stock.harness.InvestmentHarness;
import com.stock.harness.persistence.HarnessDecisionSnapshot;
import com.stock.harness.persistence.HarnessCurrentPriceSnapshot;
import com.stock.harness.persistence.HarnessMarketSnapshot;
import com.stock.harness.persistence.HarnessPortfolioPositionSnapshot;
import com.stock.harness.persistence.HarnessPortfolioSnapshot;
import com.stock.harness.persistence.HarnessRiskCheckSnapshot;
import com.stock.harness.persistence.HarnessToolExecutionSnapshot;
import com.stock.harness.persistence.HarnessToolRequestSnapshot;
import com.stock.harness.tool.HarnessToolExecutionResult;
import com.stock.harness.tool.HarnessToolExecutionReasonCode;
import com.stock.harness.tool.HarnessToolExecutionStatus;
import com.stock.harness.tool.HarnessToolOutput;
import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import com.stock.market.MarketSnapshot;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.lookup.CurrentPriceLookupResult;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisResult;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverage;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeRecord;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HarnessController.class)
class HarnessControllerTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private static final Instant CURRENT_PRICE_OBSERVED_AT = Instant.parse(
            "2026-01-01T00:00:00Z"
    );

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

        when(investmentHarness.run(STRATEGY_IDENTITY))
                .thenReturn(completedRun(runId));
        when(tradeHistoryService.getRecordsByRunId(runId))
                .thenReturn(List.of(executedBuyTradeRecord(runId)));

        mockMvc.perform(post("/api/harness/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId": "DAY_TRADING_V1",
                                  "strategyVersion": 1,
                                  "horizon": "DAY_TRADING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.strategyIdentity.strategyId").value("DAY_TRADING_V1"))
                .andExpect(jsonPath("$.strategyIdentity.strategyVersion").value(1))
                .andExpect(jsonPath("$.strategyIdentity.horizon").value("DAY_TRADING"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidateSymbols[0]").value("005930"))
                .andExpect(jsonPath("$.candidateSymbols[1]").value("000660"))
                .andExpect(jsonPath("$.toolResults[0].status").value("EXECUTED"))
                .andExpect(jsonPath("$.toolResults[0].type").value("GET_PORTFOLIO"))
                .andExpect(jsonPath("$.toolResults[0].request.type").value("GET_PORTFOLIO"))
                .andExpect(jsonPath("$.toolResults[0].reasonCode").value("TOOL_EXECUTED"))
                .andExpect(jsonPath("$.toolResults[0].output.portfolioSnapshot.cashAmountKrw")
                        .value(9_300_000L))
                .andExpect(jsonPath("$.toolResults[1].output.currentPriceSnapshot.symbol")
                        .value("005930"))
                .andExpect(jsonPath("$.toolResults[1].output.currentPriceSnapshot.observedAt")
                        .value(CURRENT_PRICE_OBSERVED_AT.toString()))
                .andExpect(jsonPath("$.toolResults[1].output.currentPriceSource")
                        .value("PROVIDER"))
                .andExpect(jsonPath(
                        "$.decision.movingAverageEvidence.analysis.status"
                ).value("ANALYZED"))
                .andExpect(jsonPath(
                        "$.decision.movingAverageEvidence.analysis.trend"
                ).value("UPTREND"))
                .andExpect(jsonPath(
                        "$.decision.movingAverageEvidence.currentPriceKrw"
                ).value(72_000L))
                .andExpect(jsonPath("$.tradeRecords[0].runId").value(runId))
                .andExpect(jsonPath("$.tradeRecords[0].status").value("EXECUTED"));

        verify(investmentHarness).run(STRATEGY_IDENTITY);
        verify(tradeHistoryService).getRecordsByRunId(runId);
    }

    @Test
    void runRejectsInvalidStrategyRequest() throws Exception {
        mockMvc.perform(post("/api/harness/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "strategyId": " ",
                                  "strategyVersion": 0,
                                  "horizon": null
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(investmentHarness, never()).run(any());
    }

    @Test
    void getRunsWithoutTradeRecords() throws Exception {
        String runId = "run-1";

        when(harnessRunHistoryService.getRunSummaries())
                .thenReturn(List.of(completedSummary(runId)));

        mockMvc.perform(get("/api/harness/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId))
                .andExpect(jsonPath("$[0].strategyIdentity.strategyId")
                        .value(STRATEGY_IDENTITY.strategyId()))
                .andExpect(jsonPath("$[0].strategyIdentity.strategyVersion")
                        .value(STRATEGY_IDENTITY.strategyVersion()))
                .andExpect(jsonPath("$[0].strategyIdentity.horizon")
                        .value(STRATEGY_IDENTITY.horizon().name()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        verify(harnessRunHistoryService).getRunSummaries();
        verify(tradeHistoryService, never()).getRecordsByRunId(any());
    }

    @Test
    void getRunReturnsPersistedRunDetail() throws Exception {
        String runId = "run-1";

        when(harnessRunHistoryService.getRunDetail(eq(runId), any()))
                .thenReturn(Optional.of(completedRunDetail(runId)));
        when(tradeHistoryService.getRecordsByRunId(runId))
                .thenReturn(List.of(executedBuyTradeRecord(runId)));

        mockMvc.perform(get("/api/harness/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.strategyIdentity.strategyId")
                        .value(STRATEGY_IDENTITY.strategyId()))
                .andExpect(jsonPath("$.strategyIdentity.strategyVersion")
                        .value(STRATEGY_IDENTITY.strategyVersion()))
                .andExpect(jsonPath("$.strategyIdentity.horizon")
                        .value(STRATEGY_IDENTITY.horizon().name()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidateSymbols[0]").value("005930"))
                .andExpect(jsonPath("$.candidateSymbols[1]").value("000660"))
                .andExpect(jsonPath("$.decisionSnapshot.action").value("BUY"))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.status"
                ).value("ANALYZED"))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.trend"
                ).value("UPTREND"))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.previousTrend"
                ).value("FLAT"))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.previousAsOfTradingDate"
                ).value("2026-01-01"))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.crossoverSignal"
                ).value("GOLDEN_CROSS"))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.shortPeriod"
                ).value(5))
                .andExpect(jsonPath(
                        "$.decisionSnapshot.movingAverageEvidence.currentPriceSource"
                ).value("PROVIDER"))
                .andExpect(jsonPath("$.riskCheckSnapshot.status").value("APPROVED"))
                .andExpect(jsonPath("$.portfolioSnapshot.cashAmountKrw").value(9_300_000L))
                .andExpect(jsonPath("$.marketSnapshot.market").value("KR"))
                .andExpect(jsonPath("$.toolExecutionSnapshots[0].currentPriceSource")
                        .value("CACHE"))
                .andExpect(jsonPath("$.toolExecutionSnapshots[0].currentPriceSnapshot.observedAt")
                        .value(CURRENT_PRICE_OBSERVED_AT.toString()))
                .andExpect(jsonPath("$.steps[0].type").value("EXECUTE_TRADE"))
                .andExpect(jsonPath("$.tradeRecords[0].status").value("EXECUTED"));

        verify(harnessRunHistoryService).getRunDetail(eq(runId), any());
        verify(tradeHistoryService).getRecordsByRunId(runId);
    }

    @Test
    void getRunReturnsNotFoundWhenRunDoesNotExist() throws Exception {
        String runId = "missing-run";

        when(harnessRunHistoryService.getRunDetail(eq(runId), any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/harness/runs/{runId}", runId))
                .andExpect(status().isNotFound());

        verify(harnessRunHistoryService).getRunDetail(eq(runId), any());
        verify(tradeHistoryService).getRecordsByRunId(runId);
    }

    @Test
    void resetDelegatesToHarnessStateService() throws Exception {
        mockMvc.perform(post("/api/harness/reset"))
                .andExpect(status().isOk());

        verify(harnessStateService).reset();
    }

    @Test
    void getStepsWithRunId() throws Exception {
        String runId = "run-1";

        when(harnessRunHistoryService.existsRun(runId))
                .thenReturn(true);
        when(harnessRunHistoryService.getStepsByRunId(runId))
                .thenReturn(completedSteps());

        mockMvc.perform(get("/api/harness/runs/{runId}/steps", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("EXECUTE_TRADE"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].message").value("Trade execution completed."));

        verify(harnessRunHistoryService).existsRun(runId);
        verify(harnessRunHistoryService).getStepsByRunId(runId);
    }

    @Test
    void getStepsNotFoundRunId() throws Exception {
        String runId = "run-1";

        when(harnessRunHistoryService.existsRun(runId))
                .thenReturn(false);

        mockMvc.perform(get("/api/harness/runs/{runId}/steps", runId))
                .andExpect(status().isNotFound());

        verify(harnessRunHistoryService).existsRun(runId);
        verify(harnessRunHistoryService, never()).getStepsByRunId(any());
    }

    private HarnessRunResult completedRun(String runId) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return HarnessRunResult.of(
                runId,
                STRATEGY_IDENTITY,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt,
                List.of("005930", "000660"),
                List.of(completedStep()),
                List.of(
                        portfolioToolExecutionResult(),
                        currentPriceToolExecutionResult()
                ),
                buyDecision(),
                approvedRiskCheckResult(),
                executedBuyTradeResult(),
                portfolioSnapshot(),
                marketSnapshot()
        );
    }

    private HarnessToolExecutionResult portfolioToolExecutionResult() {
        return HarnessToolExecutionResult.executed(
                HarnessToolOutput.portfolio(portfolioSnapshot())
        );
    }

    private HarnessToolExecutionResult currentPriceToolExecutionResult() {
        return HarnessToolExecutionResult.executed(
                HarnessToolRequest.currentPrice("005930"),
                HarnessToolOutput.currentPrice(
                        CurrentPriceLookupResult.provider(
                                new CurrentPriceSnapshot(
                                        "005930",
                                        70_000L,
                                        CURRENT_PRICE_OBSERVED_AT
                                )
                        )
                )
        );
    }

    private HarnessRunSummary completedSummary(String runId) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return new HarnessRunSummary(
                runId,
                STRATEGY_IDENTITY,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt
        );
    }

    private HarnessRunDetail completedRunDetail(String runId) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 1, 1, 9, 0);
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return new HarnessRunDetail(
                runId,
                STRATEGY_IDENTITY,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt,
                List.of("005930", "000660"),
                decisionSnapshot(),
                riskCheckSnapshot(),
                harnessPortfolioSnapshot(),
                harnessMarketSnapshot(),
                List.of(currentPriceToolExecutionSnapshot()),
                completedSteps(),
                List.of(executedBuyTradeRecord(runId))
        );
    }

    private HarnessToolExecutionSnapshot currentPriceToolExecutionSnapshot() {
        return new HarnessToolExecutionSnapshot(
                HarnessToolExecutionStatus.EXECUTED,
                HarnessToolType.GET_CURRENT_PRICE,
                HarnessToolExecutionReasonCode.TOOL_EXECUTED,
                "Harness tool execution completed.",
                null,
                null,
                new HarnessCurrentPriceSnapshot(
                        "005930",
                        70_000L,
                        CURRENT_PRICE_OBSERVED_AT
                ),
                new HarnessToolRequestSnapshot(HarnessToolType.GET_CURRENT_PRICE, "005930"),
                CurrentPriceLookupSource.CACHE
        );
    }

    private HarnessDecisionSnapshot decisionSnapshot() {
        return HarnessDecisionSnapshot.from(buyDecision());
    }

    private HarnessRiskCheckSnapshot riskCheckSnapshot() {
        return HarnessRiskCheckSnapshot.from(approvedRiskCheckResult());
    }

    private HarnessPortfolioSnapshot harnessPortfolioSnapshot() {
        return new HarnessPortfolioSnapshot(
                9_300_000L,
                10_000_000L,
                List.of(samsungPositionSnapshot())
        );
    }

    private HarnessPortfolioPositionSnapshot samsungPositionSnapshot() {
        return new HarnessPortfolioPositionSnapshot(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }

    private HarnessMarketSnapshot harnessMarketSnapshot() {
        return new HarnessMarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private HarnessStepResult completedStep() {
        LocalDateTime recordedAt = LocalDateTime.now();

        return new HarnessStepResult(
                HarnessStepType.EXECUTE_TRADE,
                HarnessStepStatus.COMPLETED,
                "Trade execution completed.",
                recordedAt,
                recordedAt
        );
    }

    private List<HarnessStepResult> completedSteps() {
        return List.of(completedStep());
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "005930",
                10L,
                70_000L,
                "Buy Samsung Electronics.",
                MovingAverageDecisionEvidence.analyzed(
                        movingAverageAnalysisResult(),
                        72_000L,
                        CurrentPriceLookupSource.PROVIDER
                )
        );
    }

    private MovingAverageAnalysisResult movingAverageAnalysisResult() {
        LocalDate asOfTradingDate = LocalDate.of(2026, 1, 2);
        return MovingAverageAnalysisResult.analyzed(
                STRATEGY_IDENTITY,
                60,
                movingAverageIndicator(
                        asOfTradingDate.minusDays(1),
                        "70000.00",
                        "70000.00"
                ),
                MovingAverageTrend.FLAT,
                movingAverageIndicator(
                        asOfTradingDate,
                        "71000.00",
                        "70000.00"
                ),
                MovingAverageTrend.UPTREND,
                MovingAverageCrossoverSignal.GOLDEN_CROSS
        );
    }

    private MovingAverageIndicator movingAverageIndicator(
            LocalDate asOfTradingDate,
            String shortAveragePriceKrw,
            String longAveragePriceKrw
    ) {
        return new MovingAverageIndicator(
                "005930",
                asOfTradingDate,
                new SimpleMovingAverage(
                        "005930",
                        5,
                        new BigDecimal(shortAveragePriceKrw),
                        asOfTradingDate.minusDays(4),
                        asOfTradingDate
                ),
                new SimpleMovingAverage(
                        "005930",
                        20,
                        new BigDecimal(longAveragePriceKrw),
                        asOfTradingDate.minusDays(19),
                        asOfTradingDate
                )
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
