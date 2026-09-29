package com.stock.backtest.context.portfolio;

import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record BacktestPortfolioEvaluationContext(
        LocalDate evaluationDate,
        Instant evaluatedAt,
        PortfolioSnapshot portfolioSnapshot,
        List<CurrentPriceSnapshot> currentPrices
) {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    public BacktestPortfolioEvaluationContext {
        Objects.requireNonNull(
                evaluationDate,
                "evaluationDate must not be null."
        );
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        Objects.requireNonNull(
                portfolioSnapshot,
                "portfolioSnapshot must not be null."
        );
        currentPrices = List.copyOf(Objects.requireNonNull(
                currentPrices,
                "currentPrices must not be null."
        ));
        if (!evaluatedAt.atZone(MARKET_ZONE)
                .toLocalDate()
                .equals(evaluationDate)) {
            throw new IllegalArgumentException(
                    "evaluatedAt must belong to evaluationDate in Asia/Seoul."
            );
        }

        Map<String, CurrentPriceSnapshot> pricesBySymbol =
                indexCurrentPrices(currentPrices, evaluatedAt);
        validatePortfolio(portfolioSnapshot, pricesBySymbol);
    }

    public Optional<CurrentPriceSnapshot> findCurrentPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        return currentPrices.stream()
                .filter(currentPrice -> symbol.equals(currentPrice.symbol()))
                .findFirst();
    }

    private static Map<String, CurrentPriceSnapshot> indexCurrentPrices(
            List<CurrentPriceSnapshot> currentPrices,
            Instant evaluatedAt
    ) {
        Map<String, CurrentPriceSnapshot> pricesBySymbol = new HashMap<>();
        for (CurrentPriceSnapshot currentPrice : currentPrices) {
            Objects.requireNonNull(
                    currentPrice,
                    "currentPrice must not be null."
            );
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
            Objects.requireNonNull(
                    currentPrice.observedAt(),
                    "currentPrice.observedAt must not be null."
            );
            if (!currentPrice.observedAt().equals(evaluatedAt)) {
                throw new IllegalArgumentException(
                        "currentPrice.observedAt must match evaluatedAt. symbol="
                                + currentPrice.symbol()
                );
            }
            if (pricesBySymbol.putIfAbsent(
                    currentPrice.symbol(),
                    currentPrice
            ) != null) {
                throw new IllegalArgumentException(
                        "currentPrices must not contain duplicate symbol. symbol="
                                + currentPrice.symbol()
                );
            }
        }
        return Map.copyOf(pricesBySymbol);
    }

    private static void validatePortfolio(
            PortfolioSnapshot portfolioSnapshot,
            Map<String, CurrentPriceSnapshot> pricesBySymbol
    ) {
        if (portfolioSnapshot.cashAmountKrw() < 0) {
            throw new IllegalArgumentException(
                    "portfolioSnapshot.cashAmountKrw must not be negative."
            );
        }
        Objects.requireNonNull(
                portfolioSnapshot.positions(),
                "portfolioSnapshot.positions must not be null."
        );

        long positionEvaluationAmountKrw = 0L;
        Set<String> positionSymbols = new HashSet<>();
        for (PortfolioPosition position : portfolioSnapshot.positions()) {
            Objects.requireNonNull(position, "position must not be null.");
            validatePosition(position);
            if (!positionSymbols.add(position.symbol())) {
                throw new IllegalArgumentException(
                        "portfolioSnapshot.positions must not contain "
                                + "duplicate symbol. symbol="
                                + position.symbol()
                );
            }
            CurrentPriceSnapshot currentPrice = pricesBySymbol.get(
                    position.symbol()
            );
            if (currentPrice == null) {
                throw new IllegalArgumentException(
                        "Current price is required for backtest position. symbol="
                                + position.symbol()
                );
            }
            positionEvaluationAmountKrw = Math.addExact(
                    positionEvaluationAmountKrw,
                    Math.multiplyExact(
                            position.quantity(),
                            currentPrice.priceKrw()
                    )
            );
        }
        long expectedTotalAssetAmountKrw = Math.addExact(
                portfolioSnapshot.cashAmountKrw(),
                positionEvaluationAmountKrw
        );
        if (portfolioSnapshot.totalAssetAmountKrw()
                != expectedTotalAssetAmountKrw) {
            throw new IllegalArgumentException(
                    "portfolioSnapshot.totalAssetAmountKrw must match "
                            + "cash and evaluated positions."
            );
        }
    }

    private static void validatePosition(PortfolioPosition position) {
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
        long expectedAcquisitionAmountKrw = Math.multiplyExact(
                position.quantity(),
                position.averagePriceKrw()
        );
        if (position.marketValueKrw() != expectedAcquisitionAmountKrw) {
            throw new IllegalArgumentException(
                    "position.marketValueKrw must match quantity and "
                            + "averagePriceKrw. symbol="
                            + position.symbol()
            );
        }
    }
}
