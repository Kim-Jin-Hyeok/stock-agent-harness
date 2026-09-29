package com.stock.backtest.context.portfolio;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.market.price.CurrentPriceSnapshot;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.portfolio.PortfolioPosition;
import com.stock.portfolio.PortfolioSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class BacktestPortfolioEvaluationContextFactory {
    public BacktestPortfolioEvaluationContext create(
            BacktestPortfolioState portfolioState,
            LocalDate evaluationDate,
            Instant evaluatedAt,
            Map<String, DailyPriceBar> dailyPriceBarsBySymbol
    ) {
        Objects.requireNonNull(
                portfolioState,
                "portfolioState must not be null."
        );
        Objects.requireNonNull(
                evaluationDate,
                "evaluationDate must not be null."
        );
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null.");
        Objects.requireNonNull(
                dailyPriceBarsBySymbol,
                "dailyPriceBarsBySymbol must not be null."
        );

        validateDailyPriceBars(
                evaluationDate,
                dailyPriceBarsBySymbol
        );
        validatePositionPrices(
                portfolioState,
                dailyPriceBarsBySymbol
        );

        List<PortfolioPosition> positions = toPortfolioPositions(
                portfolioState.positions()
        );
        long positionEvaluationAmountKrw = positions.stream()
                .mapToLong(position -> Math.multiplyExact(
                        position.quantity(),
                        dailyPriceBarsBySymbol
                                .get(position.symbol())
                                .closePriceKrw()
                ))
                .reduce(0L, Math::addExact);
        PortfolioSnapshot portfolioSnapshot = new PortfolioSnapshot(
                portfolioState.cashAmountKrw(),
                Math.addExact(
                        portfolioState.cashAmountKrw(),
                        positionEvaluationAmountKrw
                ),
                positions
        );
        List<CurrentPriceSnapshot> currentPrices =
                toCurrentPriceSnapshots(
                        evaluatedAt,
                        dailyPriceBarsBySymbol
                );

        return new BacktestPortfolioEvaluationContext(
                evaluationDate,
                evaluatedAt,
                portfolioSnapshot,
                currentPrices
        );
    }

    private void validateDailyPriceBars(
            LocalDate evaluationDate,
            Map<String, DailyPriceBar> dailyPriceBarsBySymbol
    ) {
        for (Map.Entry<String, DailyPriceBar> entry
                : dailyPriceBarsBySymbol.entrySet()) {
            String symbol = entry.getKey();
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException(
                        "dailyPriceBarsBySymbol key must not be blank."
                );
            }
            DailyPriceBar bar = Objects.requireNonNull(
                    entry.getValue(),
                    "dailyPriceBar must not be null. symbol=" + symbol
            );
            if (!bar.tradingDate().equals(evaluationDate)) {
                throw new IllegalArgumentException(
                        "dailyPriceBar.tradingDate must match evaluationDate. "
                                + "symbol="
                                + symbol
                );
            }
        }
    }

    private void validatePositionPrices(
            BacktestPortfolioState portfolioState,
            Map<String, DailyPriceBar> dailyPriceBarsBySymbol
    ) {
        for (BacktestPosition position : portfolioState.positions()) {
            if (!dailyPriceBarsBySymbol.containsKey(position.symbol())) {
                throw new IllegalArgumentException(
                        "Daily price bar is required for backtest position. "
                                + "symbol="
                                + position.symbol()
                );
            }
        }
    }

    private List<PortfolioPosition> toPortfolioPositions(
            List<BacktestPosition> backtestPositions
    ) {
        List<PortfolioPosition> positions = new ArrayList<>();
        for (BacktestPosition position : backtestPositions) {
            long acquisitionAmountKrw = Math.multiplyExact(
                    position.quantity(),
                    position.averageExecutionPriceKrw()
            );
            positions.add(new PortfolioPosition(
                    position.symbol(),
                    position.quantity(),
                    position.averageExecutionPriceKrw(),
                    acquisitionAmountKrw
            ));
        }
        return List.copyOf(positions);
    }

    private List<CurrentPriceSnapshot> toCurrentPriceSnapshots(
            Instant evaluatedAt,
            Map<String, DailyPriceBar> dailyPriceBarsBySymbol
    ) {
        return dailyPriceBarsBySymbol.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.naturalOrder()
                ))
                .map(entry -> new CurrentPriceSnapshot(
                        entry.getKey(),
                        entry.getValue().closePriceKrw(),
                        evaluatedAt
                ))
                .toList();
    }
}
