package com.stock.agent.decision.movingaverage;

import com.stock.agent.InvestmentAction;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MovingAverageActionPolicy {

    public InvestmentAction decide(
            MovingAverageCrossoverSignal crossoverSignal,
            String symbol,
            PortfolioSnapshot portfolioSnapshot
    ) {
        Objects.requireNonNull(
                crossoverSignal,
                "crossoverSignal must not be null."
        );
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );

        boolean hasPosition = portfolioSnapshot.positionQuantity(symbol) > 0;
        return switch (crossoverSignal) {
            case GOLDEN_CROSS -> hasPosition
                    ? InvestmentAction.HOLD
                    : InvestmentAction.BUY;
            case DEAD_CROSS -> hasPosition
                    ? InvestmentAction.SELL
                    : InvestmentAction.HOLD;
            case NONE -> InvestmentAction.HOLD;
        };
    }
}
