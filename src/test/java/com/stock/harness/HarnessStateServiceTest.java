package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class HarnessStateServiceTest {
    private final PortfolioSnapshotStore portfolioSnapshotStore = new PortfolioSnapshotStore();
    private final PortfolioService portfolioService = new PortfolioService(portfolioSnapshotStore);
    private final TradeHistoryService tradeHistoryService = new TradeHistoryService();
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService();
    private final HarnessStateService harnessStateService = new HarnessStateService(
            portfolioService,
            tradeHistoryService,
            harnessRunHistoryService
    );

    @Test
    void resetClearsPortfolioTradeHistoryAndRunHistory() {
        portfolioService.applyBuy(
                "TEST",
                10L,
                100L
        );

        tradeHistoryService.record(
                "run-1",
                executedBuyTradeResult()
        );

        harnessRunHistoryService.record(completedRun("run-1"));

        harnessStateService.reset();

        assertThat(portfolioService.getCurrentSnapshot().positions()).isEmpty();
        assertThat(tradeHistoryService.getRecords()).isEmpty();
        assertThat(harnessRunHistoryService.getRuns()).isEmpty();
    }

    private TradeResult executedBuyTradeResult() {
        return new TradeResult(
                TradeStatus.EXECUTED,
                InvestmentAction.BUY,
                "TEST",
                10L,
                100_000L,
                1_000_000L,
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution is complete."
        );
    }

    private HarnessRunResult completedRun(String runId) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime finishedAt = startedAt.plusSeconds(1);

        return HarnessRunResult.of(
                runId,
                HarnessRunStatus.COMPLETED,
                startedAt,
                finishedAt,
                List.of(completedStep()),
                null,
                null,
                null,
                null,
                null
        );
    }

    private HarnessStepResult completedStep() {
        return new HarnessStepResult(
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepStatus.COMPLETED,
                "Test completed step."
        );
    }
}
