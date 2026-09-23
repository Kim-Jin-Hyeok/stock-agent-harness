package com.stock.trade;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.risk.RiskCheckResult;
import com.stock.risk.RiskCheckStatus;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.execution.TradeExecutionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TradeExecutor {
    private final TradeExecutionHandler tradeExecutionHandler;
    private final TradeHistoryService tradeHistoryService;

    public TradeResult execute(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            InvestmentDecision decision,
            RiskCheckResult riskCheckResult
    ) {
        Objects.requireNonNull(strategyIdentity, "strategyIdentity must not be null.");

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

        if (decision.action() != InvestmentAction.BUY
                && decision.action() != InvestmentAction.SELL) {
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

        TradeResult tradeResult = Objects.requireNonNull(
                tradeExecutionHandler.execute(
                        runId,
                        strategyIdentity,
                        decision
                ),
                "tradeExecutionHandler result must not be null."
        );
        return recordAndReturn(runId, tradeResult);
    }

    private TradeResult recordAndReturn(String runId, TradeResult tradeResult) {
        tradeHistoryService.record(runId, tradeResult);

        return tradeResult;
    }
}
