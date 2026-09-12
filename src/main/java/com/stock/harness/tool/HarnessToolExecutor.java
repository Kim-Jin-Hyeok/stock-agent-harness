package com.stock.harness.tool;

import com.stock.market.MarketService;
import com.stock.market.price.CurrentPriceService;
import com.stock.portfolio.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarnessToolExecutor {
    private final PortfolioService portfolioService;
    private final MarketService marketService;
    private final CurrentPriceService currentPriceService;

    public HarnessToolExecutionResult execute(HarnessToolRequest request) {
        try {
            return switch (request.type()) {
                case GET_PORTFOLIO -> HarnessToolExecutionResult.executed(
                        HarnessToolOutput.portfolio(portfolioService.getCurrentSnapshot())
                );
                case GET_MARKET -> HarnessToolExecutionResult.executed(
                        HarnessToolOutput.market(marketService.getCurrentSnapshot())
                );
                case GET_CURRENT_PRICE -> HarnessToolExecutionResult.executed(
                        HarnessToolOutput.currentPrice(
                                currentPriceService.getCurrentPrice(request.symbol())
                        )
                );
            };
        } catch (RuntimeException e) {
            return HarnessToolExecutionResult.executionFailed(
                    request.type(),
                    e.getMessage()
            );
        }
    }
}
