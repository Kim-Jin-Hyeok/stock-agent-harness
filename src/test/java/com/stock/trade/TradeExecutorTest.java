package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.portfolio.PortfolioService;
import com.stock.portfolio.PortfolioSnapshotStore;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TradeExecutorTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity("DAY_TRADING_V1", 1, InvestmentHorizon.DAY_TRADING);
    private final PortfolioSnapshotStore store = new PortfolioSnapshotStore();
    private final PortfolioService portfolioService = new PortfolioService(store);
    private final TradeRecordRepository tradeRecordRepository = mock(TradeRecordRepository.class);
    private final TradeHistoryService tradeHistoryService = new TradeHistoryService(tradeRecordRepository);
    private final TradeExecutor tradeExecutor = new TradeExecutor(
            portfolioService, tradeHistoryService
    );

    @Test
    void riskDeniedDecisionIsRejected() {
        InvestmentDecision decision = holdDecision();

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                deniedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.RISK_DENIED);
    }

    @Test
    void holdDecisionIsSkipped() {
        InvestmentDecision decision = holdDecision();

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.HOLD_NO_ORDER);
    }

    @Test
    void approvedBuyDecisionIsExecuted() {
        InvestmentDecision decision = buyDecision();

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.EXECUTION_COMPLETED);

        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).cashAmountKrw()).isEqualTo(10_000_000L - decision.estimatedOrderAmountKrw());

        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions()).hasSize(1);
        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions().getFirst().symbol()).isEqualTo("TEST");

        verify(tradeRecordRepository).save(any());
    }

    @Test
    void approvedSellDecisionIsExecuted() {
        portfolioService.applyBuy(
                STRATEGY_IDENTITY,
                "TEST",
                15L,
                50_000L
        );

        long buyingCashAmountKrw = 10_000_000L
                - portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).cashAmountKrw();

        InvestmentDecision decision = sellDecision();

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.EXECUTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.EXECUTION_COMPLETED);

        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).cashAmountKrw()).isEqualTo(10_000_000L - buyingCashAmountKrw + decision.estimatedOrderAmountKrw());

        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions()).hasSize(1);
        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions().getFirst().symbol()).isEqualTo("TEST");
        assertThat(portfolioService.getCurrentSnapshot(STRATEGY_IDENTITY).positions().getFirst().quantity()).isEqualTo(5L);

        verify(tradeRecordRepository).save(any());
    }

    private InvestmentDecision holdDecision() {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                null,
                "Test HOLD decision."
        );
    }

    private InvestmentDecision buyDecision() {
        return new InvestmentDecision(
                InvestmentAction.BUY,
                "TEST",
                10L,
                100_000L,
                "Test BUY decision."
        );
    }

    private InvestmentDecision sellDecision() {
        return new InvestmentDecision(
                InvestmentAction.SELL,
                "TEST",
                10L,
                100_000L,
                "Test SELL decision."
        );
    }

    private RiskCheckResult approvedRiskCheckResult(InvestmentDecision decision) {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                RiskReasonCode.RISK_APPROVED,
                "Test risk approved."
        );
    }

    private RiskCheckResult deniedRiskCheckResult(InvestmentDecision decision) {
        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                RiskReasonCode.MAX_ORDER_RATIO_EXCEEDED,
                "Test risk denied."
        );
    }
}
