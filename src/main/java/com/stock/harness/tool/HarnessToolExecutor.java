package com.stock.harness.tool;

import com.stock.market.MarketService;
import com.stock.portfolio.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarnessToolExecutor {
    private final PortfolioService portfolioService;
    private final MarketService marketService;

    public HarnessToolExecutionResult execute(HarnessToolRequest request) {
        return switch (request.type()) {
            case GET_PORTFOLIO -> HarnessToolExecutionResult.executed(
                    HarnessToolOutput.portfolio(portfolioService.getCurrentSnapshot())
            );
            case GET_MARKET -> HarnessToolExecutionResult.executed(
                    HarnessToolOutput.market(marketService.getCurrentSnapshot())
            );
        };
    }
}
