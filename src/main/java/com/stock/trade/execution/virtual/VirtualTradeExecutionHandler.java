package com.stock.trade.execution.virtual;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.portfolio.PortfolioService;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeReasonCode;
import com.stock.trade.TradeResult;
import com.stock.trade.TradeStatus;
import com.stock.trade.execution.TradeExecutionHandler;

import java.util.Objects;

public class VirtualTradeExecutionHandler implements TradeExecutionHandler {
    private final PortfolioService portfolioService;

    public VirtualTradeExecutionHandler(PortfolioService portfolioService) {
        this.portfolioService = Objects.requireNonNull(
                portfolioService,
                "portfolioService must not be null."
        );
    }

    @Override
    public TradeResult execute(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            InvestmentDecision decision
    ) {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(decision, "decision must not be null.");

        if (decision.action() == InvestmentAction.BUY) {
            portfolioService.applyBuy(
                    strategyIdentity,
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw()
            );
        } else if (decision.action() == InvestmentAction.SELL) {
            portfolioService.applySell(
                    strategyIdentity,
                    decision.symbol(),
                    decision.quantity(),
                    decision.expectedPriceKrw()
            );
        } else {
            throw new IllegalArgumentException(
                    "Virtual trade execution requires BUY or SELL action."
            );
        }

        return new TradeResult(
                TradeStatus.EXECUTED,
                decision.action(),
                decision.symbol(),
                decision.quantity(),
                decision.expectedPriceKrw(),
                decision.estimatedOrderAmountKrw(),
                TradeReasonCode.EXECUTION_COMPLETED,
                decision.action() + " execution is complete."
        );
    }
}
