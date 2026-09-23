package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.execution.TradeExecutionHandler;
import com.stock.trade.persistence.TradeRecordRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TradeExecutorTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );

    private final TradeExecutionHandler tradeExecutionHandler =
            mock(TradeExecutionHandler.class);
    private final TradeRecordRepository tradeRecordRepository =
            mock(TradeRecordRepository.class);
    private final TradeHistoryService tradeHistoryService =
            new TradeHistoryService(tradeRecordRepository);
    private final TradeExecutor tradeExecutor = new TradeExecutor(
            tradeExecutionHandler,
            tradeHistoryService
    );

    @Test
    void riskDeniedDecisionIsRejectedWithoutExecution() {
        InvestmentDecision decision = holdDecision();

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                deniedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.RISK_DENIED);
        verifyNoInteractions(tradeExecutionHandler);
        verify(tradeRecordRepository).save(any());
    }

    @Test
    void holdDecisionIsSkippedWithoutExecution() {
        InvestmentDecision decision = holdDecision();

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.HOLD_NO_ORDER);
        verifyNoInteractions(tradeExecutionHandler);
        verify(tradeRecordRepository).save(any());
    }

    @Test
    void approvedOrderDecisionIsDelegatedAndRecorded() {
        InvestmentDecision decision = buyDecision();
        TradeResult expected = executedResult(decision);
        when(tradeExecutionHandler.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision
        )).thenReturn(expected);

        TradeResult result = tradeExecutor.execute(
                "abc",
                STRATEGY_IDENTITY,
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result).isSameAs(expected);
        verify(tradeExecutionHandler).execute(
                "abc",
                STRATEGY_IDENTITY,
                decision
        );
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

    private TradeResult executedResult(InvestmentDecision decision) {
        return new TradeResult(
                TradeStatus.EXECUTED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                TradeReasonCode.EXECUTION_COMPLETED,
                "BUY execution is complete."
        );
    }

    private RiskCheckResult approvedRiskCheckResult(
            InvestmentDecision decision
    ) {
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

    private RiskCheckResult deniedRiskCheckResult(
            InvestmentDecision decision
    ) {
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
