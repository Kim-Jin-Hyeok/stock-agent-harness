package com.stock.harness.tool;

import com.stock.harness.execution.limit.HarnessProviderCallBudget;
import com.stock.harness.execution.limit.HarnessProviderCallLimitExceededException;
import com.stock.market.MarketService;
import com.stock.market.price.CurrentPriceService;
import com.stock.market.price.provider.error.CurrentPriceProviderException;
import com.stock.portfolio.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HarnessToolExecutor {
    private final PortfolioService portfolioService;
    private final MarketService marketService;
    private final CurrentPriceService currentPriceService;

    public HarnessToolExecutionResult execute(
            HarnessToolRequest request,
            HarnessProviderCallBudget providerCallBudget
    ) {
        try {
            return switch (request.type()) {
                case GET_PORTFOLIO -> HarnessToolExecutionResult.executed(
                        request,
                        HarnessToolOutput.portfolio(portfolioService.getCurrentSnapshot())
                );
                case GET_MARKET -> HarnessToolExecutionResult.executed(
                        request,
                        HarnessToolOutput.market(marketService.getCurrentSnapshot())
                );
                case GET_CURRENT_PRICE -> HarnessToolExecutionResult.executed(
                        request,
                        HarnessToolOutput.currentPrice(
                                currentPriceService.getCurrentPrice(
                                        request.symbol(),
                                        () -> consumeProviderCallBudget(providerCallBudget)
                                )
                        )
                );
            };
        } catch (HarnessProviderCallLimitExceededException e) {
            return HarnessToolExecutionResult.providerCallLimitExceeded(
                    request,
                    e.getMessage()
            );
        } catch (CurrentPriceProviderException e) {
            return switch (e.failureType()) {
                case TEMPORARY -> HarnessToolExecutionResult.providerTemporaryFailure(
                        request,
                        e.getMessage()
                );
                case PERMANENT -> HarnessToolExecutionResult.providerPermanentFailure(
                        request,
                        e.getMessage()
                );
            };
        } catch (RuntimeException e) {
            return HarnessToolExecutionResult.executionFailed(
                    request,
                    e.getMessage()
            );
        }
    }

    private void consumeProviderCallBudget(HarnessProviderCallBudget providerCallBudget) {
        if (!providerCallBudget.tryConsume()) {
            throw new HarnessProviderCallLimitExceededException(
                    providerCallBudget.usedCalls(),
                    providerCallBudget.maxCalls()
            );
        }
    }
}
