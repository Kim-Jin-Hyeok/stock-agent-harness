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
                null,
                "Fixed HOLD decision. cashAmountKrw="
                + context.portfolioSnapshot().cashAmountKrw()
                + ", marketOpen="
                + context.marketSnapshot().marketOpen()
                + ", allowedTools="
                + context.allowedTools().types()
        );
    }
}
