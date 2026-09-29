package com.stock.backtest.portfolio;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record BacktestPortfolioState(
        long cashAmountKrw,
        List<BacktestPosition> positions
) {
    public BacktestPortfolioState {
        if (cashAmountKrw < 0) {
            throw new IllegalArgumentException(
                    "cashAmountKrw must not be negative."
            );
        }
        positions = List.copyOf(Objects.requireNonNull(
                positions,
                "positions must not be null."
        ));
        validateUniqueSymbols(positions);
    }

    public static BacktestPortfolioState withCash(long cashAmountKrw) {
        return new BacktestPortfolioState(cashAmountKrw, List.of());
    }

    public Optional<BacktestPosition> findPosition(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        return positions.stream()
                .filter(position -> symbol.equals(position.symbol()))
                .findFirst();
    }

    private static void validateUniqueSymbols(
            List<BacktestPosition> positions
    ) {
        Set<String> symbols = new HashSet<>();
        for (BacktestPosition position : positions) {
            if (!symbols.add(position.symbol())) {
                throw new IllegalArgumentException(
                        "positions must not contain duplicate symbols."
                );
            }
        }
    }
}
