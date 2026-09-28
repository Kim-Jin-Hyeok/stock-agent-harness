package com.stock.agent.decision.movingaverage.provider.ai.request;

import com.stock.agent.InvestmentAction;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record MovingAverageOrderDecisionAiRequest(
        String strategyId,
        int strategyVersion,
        InvestmentHorizon horizon,
        InvestmentAction signalAction,
        String symbol,
        long currentPriceKrw,
        CurrentPriceLookupSource currentPriceSource,
        Instant currentPriceObservedAt,
        long cashAmountKrw,
        long totalAssetAmountKrw,
        long currentPositionQuantity,
        MovingAverageTrend previousTrend,
        MovingAverageTrend currentTrend,
        MovingAverageCrossoverSignal crossoverSignal,
        int shortMovingAveragePeriod,
        BigDecimal shortMovingAveragePriceKrw,
        int longMovingAveragePeriod,
        BigDecimal longMovingAveragePriceKrw,
        LocalDate analysisAsOfTradingDate,
        long maxAffordableQuantity,
        long maxOrderRatioQuantity,
        long maxPositionRatioQuantity,
        long maxAllowedQuantity,
        BigDecimal oneSharePortfolioRatio
) {
    public MovingAverageOrderDecisionAiRequest {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException(
                    "strategyId must not be blank."
            );
        }
        if (strategyVersion < 1) {
            throw new IllegalArgumentException(
                    "strategyVersion must be at least 1."
            );
        }
        Objects.requireNonNull(horizon, "horizon must not be null.");
        Objects.requireNonNull(
                signalAction,
                "signalAction must not be null."
        );
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        if (currentPriceKrw <= 0) {
            throw new IllegalArgumentException(
                    "currentPriceKrw must be positive."
            );
        }
        Objects.requireNonNull(
                currentPriceSource,
                "currentPriceSource must not be null."
        );
        Objects.requireNonNull(
                currentPriceObservedAt,
                "currentPriceObservedAt must not be null."
        );
        if (cashAmountKrw < 0 || totalAssetAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "Portfolio amounts must not be negative."
            );
        }
        if (currentPositionQuantity < 0) {
            throw new IllegalArgumentException(
                    "currentPositionQuantity must not be negative."
            );
        }
        Objects.requireNonNull(
                previousTrend,
                "previousTrend must not be null."
        );
        Objects.requireNonNull(
                currentTrend,
                "currentTrend must not be null."
        );
        Objects.requireNonNull(
                crossoverSignal,
                "crossoverSignal must not be null."
        );
        if (shortMovingAveragePeriod <= 0
                || longMovingAveragePeriod <= 0
                || shortMovingAveragePeriod >= longMovingAveragePeriod) {
            throw new IllegalArgumentException(
                    "Moving average periods must be positive and ordered."
            );
        }
        requirePositive(
                shortMovingAveragePriceKrw,
                "shortMovingAveragePriceKrw"
        );
        requirePositive(
                longMovingAveragePriceKrw,
                "longMovingAveragePriceKrw"
        );
        Objects.requireNonNull(
                analysisAsOfTradingDate,
                "analysisAsOfTradingDate must not be null."
        );
        if (maxAffordableQuantity < 0
                || maxOrderRatioQuantity < 0
                || maxPositionRatioQuantity < 0
                || maxAllowedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Order quantity capacities must not be negative."
            );
        }
        Objects.requireNonNull(
                oneSharePortfolioRatio,
                "oneSharePortfolioRatio must not be null."
        );
        if (oneSharePortfolioRatio.signum() < 0) {
            throw new IllegalArgumentException(
                    "oneSharePortfolioRatio must not be negative."
            );
        }
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null.");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive."
            );
        }
    }
}
