package com.stock.risk;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import org.springframework.stereotype.Component;

@Component
public class RiskGuard {

    public RiskCheckResult validate(InvestmentDecision decision) {
        if (decision.action() == InvestmentAction.HOLD) {
            return new RiskCheckResult(
                    RiskCheckStatus.APPROVED,
                    "HOLD decision does not require order risk validation."
            );
        }

        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                "BUY and SELL are not supported yet."
        );
    }
}
