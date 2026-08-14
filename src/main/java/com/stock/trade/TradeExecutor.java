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
    private final TradeHistoryService tradeHistoryService;

    public TradeResult execute(String runId, InvestmentDecision decision, RiskCheckResult riskCheckResult) {
        if (riskCheckResult.status() == RiskCheckStatus.DENIED) {
            TradeResult tradeResult = new TradeResult(
                    TradeStatus.REJECTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.RISK_DENIED,
                    "Risk check denied the decision."
            );

            return recordAndReturn(runId, tradeResult);
        }

        if (decision.action() == InvestmentAction.HOLD) {
            TradeResult tradeResult = new TradeResult(
                    TradeStatus.SKIPPED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.HOLD_NO_ORDER,
                    "HOLD decision does not create an order."
            );

            return recordAndReturn(runId, tradeResult);
        }

        if (decision.action() == InvestmentAction.BUY) {
            portfolioService.applyBuy(
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw()
            );

            TradeResult tradeResult = new TradeResult(
                    TradeStatus.EXECUTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.EXECUTION_COMPLETED,
                    "BUY execution is complete."
            );

            return recordAndReturn(runId, tradeResult);
        }

        if (decision.action() == InvestmentAction.SELL) {
            portfolioService.applySell(
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw()
            );

            TradeResult tradeResult = new TradeResult(
                    TradeStatus.EXECUTED,
                    decision.action(),
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw(),
                    decision.estimatedOrderAmountKrw(),
                    TradeReasonCode.EXECUTION_COMPLETED,
                    "SELL execution is complete."
            );

            return recordAndReturn(runId, tradeResult);
        }

        TradeResult tradeResult = new TradeResult(
                TradeStatus.REJECTED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                TradeReasonCode.UNSUPPORTED_ACTION,
                "Unsupported investment action."
        );

        return recordAndReturn(runId, tradeResult);
    }

    private TradeResult recordAndReturn(String runId, TradeResult tradeResult) {
        tradeHistoryService.record(runId, tradeResult);

        return tradeResult;
    }
}
