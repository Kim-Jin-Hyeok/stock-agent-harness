package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.harness.persistence.HarnessRunRepository;
import com.stock.harness.persistence.HarnessRunSnapshotJsonConverter;
import com.stock.harness.persistence.HarnessStepRepository;
import com.stock.market.price.observation.CurrentPriceObservationService;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.stock.portfolio.support.PortfolioSnapshotStoreFixture.create;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HarnessStateServiceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private final HarnessRunSnapshotJsonConverter harnessRunSnapshotJsonConverter =
            mock(HarnessRunSnapshotJsonConverter.class);
    private final HarnessRunRepository harnessRunRepository = mock(HarnessRunRepository.class);
    private final HarnessStepRepository harnessStepRepository = mock(HarnessStepRepository.class);
    private final PortfolioSnapshotStore portfolioSnapshotStore = create();
    private final PortfolioService portfolioService = new PortfolioService(portfolioSnapshotStore);
    private final TradeHistoryService tradeHistoryService = new TradeHistoryService(mock(TradeRecordRepository.class));
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService(
            harnessRunSnapshotJsonConverter,
            harnessRunRepository,
            harnessStepRepository
    );
    private final CurrentPriceObservationService currentPriceObservationService =
            mock(CurrentPriceObservationService.class);
    private final HarnessStateService harnessStateService = new HarnessStateService(
            portfolioService,
            tradeHistoryService,
            harnessRunHistoryService,
            currentPriceObservationService
    );

    @Test
    void resetClearsPortfolioTradeHistoryAndRunHistory() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
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

        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions()).isEmpty();
        assertThat(tradeHistoryService.getRecords()).isEmpty();

        verify(harnessRunRepository).deleteAll();
        verify(harnessStepRepository).deleteAll();
        verify(currentPriceObservationService).clear();
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
                STRATEGY_IDENTITY,
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
        LocalDateTime recordedAt = LocalDateTime.now();

        return new HarnessStepResult(
                HarnessStepType.CHECK_STEP_LIMIT,
                HarnessStepStatus.COMPLETED,
                "Test completed step.",
                recordedAt,
                recordedAt
        );
    }
}
