package com.stock.agent;

import com.stock.harness.HarnessRunContext;
import org.springframework.stereotype.Component;

@Component
public class InvestmentAgent {

    public InvestmentDecision decide(HarnessRunContext context) {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                "Initial investment agent returns fixed HOLD decision."
        );
    }
}
