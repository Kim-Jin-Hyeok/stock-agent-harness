package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import org.springframework.stereotype.Component;

@Component
public class TradeExecutor {

    public TradeResult execute(InvestmentDecision decision, RiskCheckResult riskCheckResult) {
        if (riskCheckResult.status() == RiskCheckStatus.DENIED) {
            return new TradeResult(
                    TradeStatus.REJECTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.RISK_DENIED,
                    "Risk check denied the decision."
            );
        }

        if (decision.action() == InvestmentAction.HOLD) {
            return new TradeResult(
                    TradeStatus.SKIPPED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.HOLD_NO_ORDER,
                    "HOLD decision does not create an order."
            );
        }

        if (decision.action() == InvestmentAction.BUY) {
            return new TradeResult(
                    TradeStatus.REJECTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.UNSUPPORTED_ACTION,
                    "BUY execution is not supported yet."
            );
        }

        if (decision.action() == InvestmentAction.SELL) {
            return new TradeResult(
                    TradeStatus.REJECTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.UNSUPPORTED_ACTION,
                    "SELL execution is not supported yet."
            );
        }

        return new TradeResult(
                TradeStatus.REJECTED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                TradeReasonCode.UNSUPPORTED_ACTION,
                "Unsupported investment action."
        );
    }
}
