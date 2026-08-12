package com.stock.harness;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentAgent;
import com.stock.agent.InvestmentDecision;
import com.stock.market.MarketService;
import com.stock.portfolio.PortfolioService;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskGuard;
import com.stock.trade.TradeExecutor;
import com.stock.trade.TradeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private final RiskGuard riskGuard = new RiskGuard(harnessProperties);
    private final TradeExecutor tradeExecutor = new TradeExecutor();
    private final PortfolioService portfolioService = new PortfolioService();
    private final MarketService marketService = new MarketService();
    private final InvestmentAgent investmentAgent = new InvestmentAgent();

    private final InvestmentHarness investmentHarness = new InvestmentHarness(
            riskGuard,
            tradeExecutor,
            portfolioService,
            marketService,
            investmentAgent,
            harnessProperties
    );

    private final InvestmentHarness failInvestmentHarness = new InvestmentHarness(
            riskGuard,
            tradeExecutor,
            portfolioService,
            marketService,
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
                new FailingInvestmentAgent(),
                harnessProperties
        );

        HarnessRunResult result = failingHarness.run();

        assertThat(result.status()).isEqualTo(HarnessRunStatus.FAILED);
        assertThat(result.steps().size()).isEqualTo(1);
        assertThat(result.steps().getFirst().type()).isEqualTo(HarnessStepType.RUN_FAILED);
        assertThat(result.steps().getFirst().status()).isEqualTo(HarnessStepStatus.FAILED);
    }

    private static class FailingInvestmentAgent extends InvestmentAgent {
        @Override
        public InvestmentDecision decide(HarnessRunContext context) {
            throw new IllegalStateException("Test agent failure");
        }
    }
}
