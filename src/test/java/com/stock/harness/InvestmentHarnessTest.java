package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.harness.persistence.HarnessRunRepository;
import com.stock.harness.persistence.HarnessRunSnapshotJsonConverter;
import com.stock.harness.persistence.HarnessStepRepository;
import com.stock.market.MarketService;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeHistoryService;
import com.stock.trade.TradeStatus;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InvestmentHarnessTest {

    private final HarnessProperties harnessProperties = new HarnessProperties(
            10,
            0.1,
            0.3
    );
    private final HarnessProperties failHarnessProperties = new HarnessProperties(
            4,
            0.1,
            0.3
    );

    private final HarnessRunRepository harnessRunRepository = mock(HarnessRunRepository.class);
    private final HarnessRunSnapshotJsonConverter harnessRunSnapshotJsonConverter =
            mock(HarnessRunSnapshotJsonConverter.class);
    private final HarnessStepRepository harnessStepRepository = mock(HarnessStepRepository.class);
    private final RiskGuard riskGuard = new RiskGuard(harnessProperties);
    private final PortfolioSnapshotStore store = new PortfolioSnapshotStore();
    private final PortfolioService portfolioService = new PortfolioService(store);
    private final TradeHistoryService tradeHistoryService = new TradeHistoryService(mock(TradeRecordRepository.class));
    private final TradeExecutor tradeExecutor = new TradeExecutor(
            portfolioService,
            tradeHistoryService
    );
    private final MarketService marketService = new MarketService();
    private final HarnessRunHistoryService harnessRunHistoryService = new HarnessRunHistoryService(
            harnessRunSnapshotJsonConverter,
            harnessRunRepository,
            harnessStepRepository
    );
    private final InvestmentAgent investmentAgent = new InvestmentAgent();

    private final InvestmentHarness investmentHarness = new InvestmentHarness(
            riskGuard,
            tradeExecutor,
            portfolioService,
            marketService,
            harnessRunHistoryService,
            investmentAgent,
            harnessProperties
    );

    private final InvestmentHarness failInvestmentHarness = new InvestmentHarness(
            riskGuard,
            tradeExecutor,
            portfolioService,
            marketService,
            harnessRunHistoryService,
            investmentAgent,
            failHarnessProperties
    );

    @Test
    void runCompletesWithHoldDecision() {
        HarnessRunResult result = investmentHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.HOLD);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.SKIPPED);
        assertThat(result.steps().size()).isEqualTo(6);

        List<HarnessStepType> stepTypes = result.steps().stream()
                .map(HarnessStepResult::type)
                .toList();

        assertThat(stepTypes).containsExactly(
                HarnessStepType.LOAD_PORTFOLIO,
                HarnessStepType.LOAD_MARKET,
                HarnessStepType.RUN_INVESTMENT_AGENT,
                HarnessStepType.VALIDATE_DECISION,
                HarnessStepType.EXECUTE_TRADE,
                HarnessStepType.CHECK_STEP_LIMIT
        );

        assertThat(harnessRunHistoryService.getRuntimeRunById(result.runId())).isPresent();
    }

    @Test
    void runFailsWhenStepLimitExceeded() {
        HarnessRunResult result = failInvestmentHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);

        HarnessStepResult lastStep = result.steps().getLast();

        assertThat(lastStep.type()).isEqualTo(HarnessStepType.CHECK_STEP_LIMIT);
        assertThat(lastStep.status()).isEqualTo(HarnessStepStatus.FAILED);
    }

    @Test
    void runFailsWhenAgentDecisionThrowsException() {
        InvestmentHarness failingHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new FailingInvestmentAgent(),
                harnessProperties
        );

        HarnessRunResult result = failingHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps().size()).isEqualTo(1);
        assertThat(result.steps().getFirst().type()).isEqualTo(HarnessStepType.RUN_FAILED);
        assertThat(result.steps().getFirst().status()).isEqualTo(HarnessStepStatus.FAILED);

        assertThat(harnessRunHistoryService.getRuntimeRunById(result.runId())).isPresent();
        assertThat(harnessRunHistoryService.getRuntimeRunById(result.runId()).get().status()).isEqualTo(HarnessRunStatus.FAILED);
    }

    @Test
    void runCompletesWhenAgentDecidesBuy() {
        InvestmentHarness successHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new BuyingInvestmentAgent(),
                harnessProperties
        );

        HarnessRunResult result = successHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.BUY);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.EXECUTED);

        boolean hasCompletedExecuteTradeStep = result.steps().stream()
                .anyMatch(harnessStepResult ->
                        harnessStepResult.type().equals(HarnessStepType.EXECUTE_TRADE)
                        && harnessStepResult.status().equals(HarnessStepStatus.COMPLETED)
                );
        assertThat(hasCompletedExecuteTradeStep).isTrue();

        boolean hasBuyingSymbol = result.portfolioSnapshot().positions().stream()
                .anyMatch(position -> position.symbol().equals(result.decision().symbol()));
        assertThat(hasBuyingSymbol).isTrue();
    }

    @Test
    void runCompletesWhenAgentDecidesSell() {
        PortfolioService sellPortfolioService = new PortfolioService(store);
        sellPortfolioService.applyBuy(
                "TEST",
                10L,
                100_000L
        );

        TradeExecutor sellTradeExecutor = new TradeExecutor(
                sellPortfolioService,
                tradeHistoryService
        );

        InvestmentHarness sellHarness = new InvestmentHarness(
                riskGuard,
                sellTradeExecutor,
                sellPortfolioService,
                marketService,
                harnessRunHistoryService,
                new SellingInvestmentAgent(),
                harnessProperties
        );

        HarnessRunResult result = sellHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.COMPLETED);
        assertThat(result.decision().action()).isEqualTo(InvestmentAction.SELL);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.APPROVED);
        assertThat(result.tradeResult().status()).isEqualTo(TradeStatus.EXECUTED);

        PortfolioPosition position = result.portfolioSnapshot().positions().getFirst();

        assertThat(position.symbol()).isEqualTo("TEST");
        assertThat(position.quantity()).isEqualTo(5L);
    }

    private static class BuyingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            return new InvestmentDecision(
                    InvestmentAction.BUY,
                    "TEST",
                    10L,
                    100_000L,
                    "Test buy complete."
            );
        }
    }

    private static class SellingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            return new InvestmentDecision(
                    InvestmentAction.SELL,
                    "TEST",
                    5L,
                    110_000L,
                    "Test sell complete."
            );
        }
    }

    private static class FailingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            throw new IllegalStateException("Test agent failure");
        }
    }
}
