package com.stock.harness;

import com.stock.market.price.observation.CurrentPriceObservationService;
import com.stock.portfolio.PortfolioService;
import com.stock.trade.TradeHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HarnessStateService {
    private final PortfolioService portfolioService;
    private final TradeHistoryService tradeHistoryService;
    private final HarnessRunHistoryService harnessRunHistoryService;
    private final CurrentPriceObservationService currentPriceObservationService;

    public void reset() {
        portfolioService.resetAll();
        tradeHistoryService.clear();
        harnessRunHistoryService.clear();
        currentPriceObservationService.clear();
    }
}
