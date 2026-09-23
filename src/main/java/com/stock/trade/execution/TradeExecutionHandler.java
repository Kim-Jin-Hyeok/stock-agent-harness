package com.stock.trade.execution;

import com.stock.agent.InvestmentDecision;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.TradeResult;

public interface TradeExecutionHandler {
    TradeResult execute(
            String runId,
            InvestmentStrategyIdentity strategyIdentity,
            InvestmentDecision decision
    );
}
