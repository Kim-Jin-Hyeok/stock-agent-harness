package com.stock.portfolio.valuation;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PortfolioValuationService {
    private final CurrentPriceFreshnessPolicy freshnessPolicy;

    public PortfolioValuationService(
            CurrentPriceFreshnessPolicy freshnessPolicy
    ) {
        this.freshnessPolicy = Objects.requireNonNull(
                freshnessPolicy,
                "freshnessPolicy must not be null."
        );
    }

    public PortfolioValuationSnapshot evaluate(
            PortfolioSnapshot portfolioSnapshot,
            List<CurrentPriceSnapshot> currentPrices,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        if (portfolioSnapshot.cashAmountKrw() < 0) {
            throw new IllegalArgumentException(
                    "portfolioSnapshot.cashAmountKrw must not be negative."
            );
        }

        Map<String, CurrentPriceSnapshot> pricesBySymbol =
                indexCurrentPrices(currentPrices, evaluatedAt);
        List<PortfolioPositionValuation> valuations =
                evaluatePositions(
                        portfolioSnapshot.positions(),
                        pricesBySymbol
                );
        long positionEvaluationAmountKrw = valuations.stream()
                .mapToLong(
                        PortfolioPositionValuation::evaluationAmountKrw
                )
                .reduce(0L, Math::addExact);
        long totalAssetAmountKrw = Math.addExact(
                portfolioSnapshot.cashAmountKrw(),
                positionEvaluationAmountKrw
        );

        return new PortfolioValuationSnapshot(
                evaluatedAt,
                portfolioSnapshot.cashAmountKrw(),
                positionEvaluationAmountKrw,
                totalAssetAmountKrw,
                valuations
        );
    }

    private Map<String, CurrentPriceSnapshot> indexCurrentPrices(
            List<CurrentPriceSnapshot> currentPrices,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(currentPrices, "currentPrices must not be null.");
        Map<String, CurrentPriceSnapshot> pricesBySymbol = new HashMap<>();

        for (CurrentPriceSnapshot currentPrice : currentPrices) {
            Objects.requireNonNull(
                    currentPrice,
                    "currentPrice must not be null."
            );
            validateCurrentPrice(currentPrice, evaluatedAt);
            if (pricesBySymbol.putIfAbsent(
                    currentPrice.symbol(),
                    currentPrice
            ) != null) {
                throw new IllegalArgumentException(
                        "currentPrices must not contain duplicate symbol. "
                                + "symbol="
                                + currentPrice.symbol()
                );
            }
        }
        return Map.copyOf(pricesBySymbol);
    }

    private List<PortfolioPositionValuation> evaluatePositions(
            List<PortfolioPosition> positions,
            Map<String, CurrentPriceSnapshot> pricesBySymbol
    ) {
        Objects.requireNonNull(positions, "positions must not be null.");
        List<PortfolioPositionValuation> valuations =
                new ArrayList<>(positions.size());
        Set<String> symbols = new HashSet<>();

        for (PortfolioPosition position : positions) {
            Objects.requireNonNull(position, "position must not be null.");
            validatePosition(position);
            if (!symbols.add(position.symbol())) {
                throw new IllegalArgumentException(
                        "positions must not contain duplicate symbol. symbol="
                                + position.symbol()
                );
            }

            CurrentPriceSnapshot currentPrice = pricesBySymbol.get(
                    position.symbol()
            );
            if (currentPrice == null) {
                throw new IllegalArgumentException(
                        "Current price is required for portfolio position. "
                                + "symbol="
                                + position.symbol()
                );
            }

            long evaluationAmountKrw = Math.multiplyExact(
                    position.quantity(),
                    currentPrice.priceKrw()
            );
            long unrealizedProfitLossKrw = Math.subtractExact(
                    evaluationAmountKrw,
                    position.marketValueKrw()
            );
            valuations.add(new PortfolioPositionValuation(
                    position.symbol(),
                    position.quantity(),
                    position.averagePriceKrw(),
                    currentPrice.priceKrw(),
                    position.marketValueKrw(),
                    evaluationAmountKrw,
                    unrealizedProfitLossKrw,
                    currentPrice.observedAt()
            ));
        }
        return List.copyOf(valuations);
    }

    private void validatePosition(PortfolioPosition position) {
        if (position.symbol() == null || position.symbol().isBlank()) {
            throw new IllegalArgumentException(
                    "position.symbol must not be blank."
            );
        }
        if (position.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "position.quantity must be positive. symbol="
                            + position.symbol()
            );
        }
        if (position.averagePriceKrw() <= 0) {
            throw new IllegalArgumentException(
                    "position.averagePriceKrw must be positive. symbol="
                            + position.symbol()
            );
        }
        if (position.marketValueKrw() <= 0) {
            throw new IllegalArgumentException(
                    "position.marketValueKrw must be positive. symbol="
                            + position.symbol()
            );
        }
    }

    private void validateCurrentPrice(
            CurrentPriceSnapshot currentPrice,
            Instant evaluatedAt
    ) {
        if (currentPrice.symbol() == null
                || currentPrice.symbol().isBlank()) {
            throw new IllegalArgumentException(
                    "currentPrice.symbol must not be blank."
            );
        }
        if (currentPrice.priceKrw() <= 0) {
            throw new IllegalArgumentException(
                    "currentPrice.priceKrw must be positive. symbol="
                            + currentPrice.symbol()
            );
        }
        if (currentPrice.observedAt() == null) {
            throw new IllegalArgumentException(
                    "currentPrice.observedAt must not be null. symbol="
                            + currentPrice.symbol()
            );
        }
        if (currentPrice.observedAt().isAfter(evaluatedAt)) {
            throw new IllegalArgumentException(
                    "currentPrice.observedAt must not be after evaluatedAt. "
                            + "symbol="
                            + currentPrice.symbol()
            );
        }
        if (!freshnessPolicy.isFreshAt(
                currentPrice.observedAt(),
                evaluatedAt
        )) {
            throw new IllegalArgumentException(
                    "Current price is stale at portfolio evaluation. symbol="
                            + currentPrice.symbol()
            );
        }
    }
}
