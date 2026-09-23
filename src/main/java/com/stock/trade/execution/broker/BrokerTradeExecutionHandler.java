package com.stock.trade.execution.broker;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.broker.order.BrokerOrderRecord;
import com.stock.broker.order.BrokerOrderRequest;
import com.stock.broker.order.BrokerOrderSide;
import com.stock.broker.order.BrokerOrderStatus;
import com.stock.broker.order.application.BrokerOrderSubmissionService;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import com.stock.trade.execution.TradeExecutionHandler;

import java.util.Objects;

public class BrokerTradeExecutionHandler implements TradeExecutionHandler {
    private final BrokerOrderSubmissionService submissionService;

    public BrokerTradeExecutionHandler(
            BrokerOrderSubmissionService submissionService
    ) {
        this.submissionService = Objects.requireNonNull(
                submissionService,
                "submissionService must not be null."
        );
    }

    @Override
    public TradeResult execute(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            InvestmentDecision decision
    ) {
        Objects.requireNonNull(decision, "decision must not be null.");

        BrokerOrderRequest request = new BrokerOrderRequest(
                toOrderSide(decision.action()),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw()
        );
        BrokerOrderRecord order = submissionService.submit(
                runId,
                strategyIdentity,
                request
        );

        if (order.status() == BrokerOrderStatus.PENDING) {
            return result(
                    decision,
                    TradeStatus.SUBMITTED,
                    TradeReasonCode.ORDER_SUBMITTED,
                    "Broker order was submitted."
            );
        }
        if (order.status() == BrokerOrderStatus.REJECTED) {
            return result(
                    decision,
                    TradeStatus.REJECTED,
                    TradeReasonCode.BROKER_ORDER_REJECTED,
                    order.reason()
            );
        }

        throw new IllegalStateException(
                "Unexpected broker order status after submission: "
                        + order.status()
        );
    }

    private BrokerOrderSide toOrderSide(InvestmentAction action) {
        if (action == InvestmentAction.BUY) {
            return BrokerOrderSide.BUY;
        }
        if (action == InvestmentAction.SELL) {
            return BrokerOrderSide.SELL;
        }
        throw new IllegalArgumentException(
                "Broker trade execution requires BUY or SELL action."
        );
    }

    private TradeResult result(
            InvestmentDecision decision,
            TradeStatus status,
            TradeReasonCode reasonCode,
            String reason
    ) {
        return new TradeResult(
                status,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                reasonCode,
                reason
        );
    }
}
