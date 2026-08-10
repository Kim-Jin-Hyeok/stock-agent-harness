package com.stock.agent;

import com.stock.market.MarketSnapshot;
import com.stock.portfolio.PortfolioSnapshot;
import org.springframework.stereotype.Component;

@Component
public class InvestmentAgent {

    public InvestmentDecision decide(PortfolioSnapshot portfolioSnapshot, MarketSnapshot marketSnapshot) {
        return new InvestmentDecision(
                InvestmentAction.HOLD,
                "Initial investment agent returns fixed HOLD decision."
        );
    }
}
