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

        if (decision.symbol() == null || decision.symbol().isBlank()) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    "BUY and SELL decisions require a symbol."
            );
        }

        if (decision.orderAmount() == null || decision.orderAmount() <= 0) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    "BUY and SELL decisions require a positive order amount."
            );
        }

        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                "BUY and SELL are not supported yet."
        );
    }
}
