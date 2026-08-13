package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.portfolio.PortfolioService;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeExecutor {
    private final PortfolioService portfolioService;

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
            portfolioService.applyBuy(
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw()
            );

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

        if (decision.action() == InvestmentAction.SELL) {
            portfolioService.applySell(
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw()
            );

            return new TradeResult(
                    TradeStatus.EXECUTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.EXECUTION_COMPLETED,
                    "SELL execution is complete."
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
