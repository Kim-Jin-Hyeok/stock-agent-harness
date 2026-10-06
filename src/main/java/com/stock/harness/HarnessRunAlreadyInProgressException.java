package com.stock.harness;

import com.stock.strategy.profile.InvestmentStrategyIdentity;

public class HarnessRunAlreadyInProgressException extends RuntimeException {
    public HarnessRunAlreadyInProgressException(InvestmentStrategyIdentity strategyIdentity) {
        super("Harness run already in progress. strategyId=" + strategyIdentity.strategyId()
                + ", strategyVersion=" + strategyIdentity.strategyVersion()
                + ", horizon=" + strategyIdentity.horizon());
    }
}
