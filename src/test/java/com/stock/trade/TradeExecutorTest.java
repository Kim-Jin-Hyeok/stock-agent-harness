package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.risk.RiskReasonCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradeExecutorTest {

    private final TradeExecutor tradeExecutor = new TradeExecutor();

    @Test
    void riskDeniedDecisionIsRejected() {
        InvestmentDecision decision = holdDecision();

        TradeResult result = tradeExecutor.execute(
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
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.SKIPPED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.HOLD_NO_ORDER);
    }

    @Test
    void approvedBuyDecisionIsRejectedBecauseBuyIsNotSupportedYet() {
        InvestmentDecision decision = buyDecision();

        TradeResult result = tradeExecutor.execute(
                decision,
                approvedRiskCheckResult(decision)
        );

        assertThat(result.status()).isEqualTo(TradeStatus.REJECTED);
        assertThat(result.reasonCode()).isEqualTo(TradeReasonCode.UNSUPPORTED_ACTION);
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

    private RiskCheckResult approvedRiskCheckResult(InvestmentDecision decision) {
        return new RiskCheckResult(
                RiskCheckStatus.APPROVED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                RiskReasonCode.HOLD_NO_ORDER_REQUIRED,
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
