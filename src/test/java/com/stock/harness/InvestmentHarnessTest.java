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
        assertThat(result.steps().size()).isEqualTo(7);

        List<HarnessStepType> stepTypes = result.steps().stream()
                .map(HarnessStepResult::type)
                .toList();

        assertThat(stepTypes).containsExactly(
                HarnessStepType.LOAD_PORTFOLIO,
                HarnessStepType.LOAD_MARKET,
                HarnessStepType.RUN_INVESTMENT_AGENT,
                HarnessStepType.VALIDATE_DECISION,
                HarnessStepType.EXECUTE_TRADE,
                HarnessStepType.LOAD_FINAL_PORTFOLIO,
                HarnessStepType.CHECK_STEP_LIMIT
        );

        HarnessStepResult loadPortfolioStep = result.steps().get(0);
        HarnessStepResult loadMarketStep = result.steps().get(1);

        assertThat(loadPortfolioStep.startedAt()).isNotNull();
        assertThat(loadPortfolioStep.finishedAt()).isNotNull();
        assertThat(loadPortfolioStep.startedAt()).isBeforeOrEqualTo(loadPortfolioStep.finishedAt());

        assertThat(loadMarketStep.startedAt()).isNotNull();
        assertThat(loadMarketStep.finishedAt()).isNotNull();
        assertThat(loadMarketStep.startedAt()).isBeforeOrEqualTo(loadMarketStep.finishedAt());

        HarnessStepResult validateDecisionStep = result.steps().get(3);

        assertThat(validateDecisionStep.type()).isEqualTo(HarnessStepType.VALIDATE_DECISION);
        assertThat(validateDecisionStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(validateDecisionStep.message()).isEqualTo("HOLD decision does not require order risk validation.");
        assertThat(validateDecisionStep.startedAt()).isNotNull();
        assertThat(validateDecisionStep.finishedAt()).isNotNull();
        assertThat(validateDecisionStep.startedAt()).isBeforeOrEqualTo(validateDecisionStep.finishedAt());

        HarnessStepResult executeTradeStep = result.steps().get(4);

        assertThat(executeTradeStep.type()).isEqualTo(HarnessStepType.EXECUTE_TRADE);
        assertThat(executeTradeStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(executeTradeStep.message()).isEqualTo("HOLD decision does not create an order.");
        assertThat(executeTradeStep.startedAt()).isNotNull();
        assertThat(executeTradeStep.finishedAt()).isNotNull();
        assertThat(executeTradeStep.startedAt()).isBeforeOrEqualTo(executeTradeStep.finishedAt());

        HarnessStepResult finalPortfolioStep = result.steps().get(5);

        assertThat(finalPortfolioStep.type()).isEqualTo(HarnessStepType.LOAD_FINAL_PORTFOLIO);
        assertThat(finalPortfolioStep.status()).isEqualTo(HarnessStepStatus.COMPLETED);
        assertThat(finalPortfolioStep.message()).isEqualTo("Final portfolio loading complete.");
        assertThat(finalPortfolioStep.startedAt()).isNotNull();
        assertThat(finalPortfolioStep.finishedAt()).isNotNull();
        assertThat(finalPortfolioStep.startedAt()).isBeforeOrEqualTo(finalPortfolioStep.finishedAt());
    }

    @Test
    void runFailsWhenStepLimitExceeded() {
        HarnessRunResult result = failInvestmentHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);

        HarnessStepResult lastStep = result.steps().getLast();

        assertThat(lastStep.type()).isEqualTo(HarnessStepType.CHECK_STEP_LIMIT);
        assertThat(lastStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(lastStep.message()).isEqualTo("Executable steps: 6, final steps: 7, max steps: 4");
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
        assertThat(result.steps())
                .extracting(HarnessStepResult::type)
                .containsExactly(
                        HarnessStepType.LOAD_PORTFOLIO,
                        HarnessStepType.LOAD_MARKET,
                        HarnessStepType.RUN_INVESTMENT_AGENT,
                        HarnessStepType.RUN_FAILED
                );
        HarnessStepResult agentStep = result.steps().get(2);

        assertThat(agentStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(agentStep.message()).isEqualTo("Test agent failure");
        assertThat(agentStep.startedAt()).isNotNull();
        assertThat(agentStep.finishedAt()).isNotNull();
        assertThat(agentStep.startedAt()).isBeforeOrEqualTo(agentStep.finishedAt());

        assertThat(result.steps().getLast().status()).isEqualTo(HarnessStepStatus.FAILED);
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

        HarnessStepResult executeTradeStep = result.steps().get(4);

        assertThat(executeTradeStep.type()).isEqualTo(HarnessStepType.EXECUTE_TRADE);
        assertThat(executeTradeStep.message()).isEqualTo("BUY execution is complete.");
        assertThat(executeTradeStep.startedAt()).isNotNull();
        assertThat(executeTradeStep.finishedAt()).isNotNull();
        assertThat(executeTradeStep.startedAt()).isBeforeOrEqualTo(executeTradeStep.finishedAt());

        boolean hasBuyingSymbol = result.portfolioSnapshot().positions().stream()
                .anyMatch(position -> position.symbol().equals(result.decision().symbol()));
        assertThat(hasBuyingSymbol).isTrue();
    }

    @Test
    void runFailsWhenRiskGuardDeniesDecision() {
        InvestmentHarness deniedHarness = new InvestmentHarness(
                riskGuard,
                tradeExecutor,
                portfolioService,
                marketService,
                harnessRunHistoryService,
                new OverLimitBuyingInvestmentAgent(),
                harnessProperties
        );

        HarnessRunResult result = deniedHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.riskCheckResult().status()).isEqualTo(RiskCheckStatus.DENIED);

        HarnessStepResult validateDecisionStep = result.steps().get(3);

        assertThat(validateDecisionStep.type()).isEqualTo(HarnessStepType.VALIDATE_DECISION);
        assertThat(validateDecisionStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(validateDecisionStep.message()).contains("Order amount exceeds max order ratio.");
        assertThat(validateDecisionStep.startedAt()).isNotNull();
        assertThat(validateDecisionStep.finishedAt()).isNotNull();
        assertThat(validateDecisionStep.startedAt()).isBeforeOrEqualTo(validateDecisionStep.finishedAt());

        HarnessStepResult executeTradeStep = result.steps().get(4);

        assertThat(executeTradeStep.type()).isEqualTo(HarnessStepType.EXECUTE_TRADE);
        assertThat(executeTradeStep.status()).isEqualTo(HarnessStepStatus.FAILED);
        assertThat(executeTradeStep.message()).isEqualTo("Risk check denied the decision.");
        assertThat(executeTradeStep.startedAt()).isNotNull();
        assertThat(executeTradeStep.finishedAt()).isNotNull();
        assertThat(executeTradeStep.startedAt()).isBeforeOrEqualTo(executeTradeStep.finishedAt());
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

    private static class OverLimitBuyingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            return new InvestmentDecision(
                    InvestmentAction.BUY,
                    "TEST",
                    20L,
                    100_000L,
                    "Test buy exceeds max order ratio."
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
