package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import org.springframework.stereotype.Component;

@Component
public class InvestmentAgent {

    public InvestmentDecision decide(HarnessRunContext context) {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                null,
                null,
                "Fixed HOLD decision. cashAmount="
                + context.portfolioSnapshot().cashAmount()
                + ", marketOpen="
                + context.marketSnapshot().marketOpen()
        );
    }
}
