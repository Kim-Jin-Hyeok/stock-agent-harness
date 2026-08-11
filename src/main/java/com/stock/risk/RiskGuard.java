package com.stock.risk;

import com.stock.agent.InvestmentAction;
import com.stock.agent.InvestmentDecision;
import com.stock.portfolio.PortfolioSnapshot;
import org.springframework.stereotype.Component;

@Component
public class RiskGuard {

    public RiskCheckResult validate(
            InvestmentDecision decision,
            PortfolioSnapshot portfolioSnapshot
    ) {
        if (decision.action() == InvestmentAction.HOLD) {
            return new RiskCheckResult(
                    RiskCheckStatus.APPROVED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    "HOLD decision does not require order risk validation."
            );
        }

        if (decision.symbol() == null || decision.symbol().isBlank()) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    "BUY and SELL decisions require a symbol."
            );
        }

        if (decision.orderAmountKrw() == null || decision.orderAmountKrw() <= 0) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    "BUY and SELL decisions require a positive order amount."
            );
        }

        if (decision.orderAmountKrw() > portfolioSnapshot.cashAmount()) {
            return new RiskCheckResult(
                    RiskCheckStatus.DENIED,
                    decision.action(),
                    decision.symbol(),
                    decision.orderAmountKrw(),
                    "Order amount exceeds available cash."
            );
        }

        return new RiskCheckResult(
                RiskCheckStatus.DENIED,
                decision.action(),
                decision.symbol(),
                decision.orderAmountKrw(),
                "BUY and SELL are not supported yet."
        );
    }
}
