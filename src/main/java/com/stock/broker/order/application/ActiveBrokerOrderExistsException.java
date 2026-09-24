package com.stock.broker.order.application;

import com.stock.broker.order.BrokerOrderSide;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

public class ActiveBrokerOrderExistsException extends RuntimeException {

    public ActiveBrokerOrderExistsException(
            InvestmentStrategyIdentity strategyIdentity,
            BrokerOrderSide side,
            String symbol
    ) {
        super(
                "Active broker order already exists. strategyId="
                        + strategyIdentity.strategyId()
                        + ", strategyVersion="
                        + strategyIdentity.strategyVersion()
                        + ", horizon="
                        + strategyIdentity.horizon()
                        + ", side="
                        + side
                        + ", symbol="
                        + symbol
        );
    }
}
