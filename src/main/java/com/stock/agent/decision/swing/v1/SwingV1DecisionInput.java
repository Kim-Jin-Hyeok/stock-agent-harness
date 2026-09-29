package com.stock.agent.decision.swing.v1;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.portfolio.PortfolioSnapshot;
import com.stock.strategy.profile.InvestmentStrategyIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SwingV1DecisionInput(
        InvestmentStrategyIdentity strategyIdentity,
        DailyPriceHistory dailyPriceHistory,
        PortfolioSnapshot portfolioSnapshot,
        CurrentPriceSnapshot candidateCurrentPrice,
        CurrentPriceLookupSource candidateCurrentPriceSource,
        List<CurrentPriceSnapshot> currentPrices,
        Instant evaluatedAt
) {
    public SwingV1DecisionInput {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        Objects.requireNonNull(
                dailyPriceHistory,
                "dailyPriceHistory must not be null."
        );
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        Objects.requireNonNull(
                candidateCurrentPrice,
                "candidateCurrentPrice must not be null."
        );
        Objects.requireNonNull(
                candidateCurrentPriceSource,
                "candidateCurrentPriceSource must not be null."
        );
        currentPrices = List.copyOf(Objects.requireNonNull(
                currentPrices,
                "currentPrices must not be null."
        ));
        Objects.requireNonNull(
                evaluatedAt,
                "evaluatedAt must not be null."
        );
    }
}
