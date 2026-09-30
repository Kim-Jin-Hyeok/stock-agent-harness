package com.stock.backtest.performance.equity;

import com.stock.backtest.context.portfolio.BacktestPortfolioEvaluationContext;
import com.stock.portfolio.PortfolioSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public record BacktestEquitySnapshot(
        LocalDate valuationDate,
        Instant evaluatedAt,
        long cashAmountKrw,
        long positionEvaluationAmountKrw,
        long totalAssetAmountKrw
) {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    public BacktestEquitySnapshot {
        Objects.requireNonNull(
                valuationDate,
                "valuationDate must not be null."
        );
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        if (!evaluatedAt.atZone(MARKET_ZONE)
                .toLocalDate()
                .equals(valuationDate)) {
            throw new IllegalArgumentException(
                    "evaluatedAt must belong to valuationDate "
                            + "in Asia/Seoul."
            );
        }
        if (cashAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "cashAmountKrw must not be negative."
            );
        }
        if (positionEvaluationAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "positionEvaluationAmountKrw must not be negative."
            );
        }
        if (totalAssetAmountKrw != Math.addExact(
                cashAmountKrw,
                positionEvaluationAmountKrw
        )) {
            throw new IllegalArgumentException(
                    "totalAssetAmountKrw must equal cash and position "
                            + "evaluation amount."
            );
        }
    }

    public static BacktestEquitySnapshot from(
            BacktestPortfolioEvaluationContext context
    ) {
        Objects.requireNonNull(context, "context must not be null.");
        PortfolioSnapshot portfolioSnapshot = context.portfolioSnapshot();
        long positionEvaluationAmountKrw = Math.subtractExact(
                portfolioSnapshot.totalAssetAmountKrw(),
                portfolioSnapshot.cashAmountKrw()
        );
        return new BacktestEquitySnapshot(
                context.evaluationDate(),
                context.evaluatedAt(),
                portfolioSnapshot.cashAmountKrw(),
                positionEvaluationAmountKrw,
                portfolioSnapshot.totalAssetAmountKrw()
        );
    }
}
