package com.stock.backtest.strategy.swing.v1.experiment.runner.config;

import com.stock.trade.cost.model.TradeCostModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "backtest.swing-v1.experiment.manual")
public record SwingV1BacktestManualRunProperties(
        boolean enabled,
        List<String> candidateSymbols,
        LocalDate fromSignalDate,
        LocalDate toSignalDate,
        String benchmarkId,
        Long initialCashAmountKrwPerSymbol,
        TradeCostModel costModel
) {
    public SwingV1BacktestManualRunProperties {
        candidateSymbols = candidateSymbols == null
                ? List.of() : List.copyOf(candidateSymbols);
        if (enabled) {
            if (candidateSymbols.isEmpty()) {
                throw new IllegalArgumentException(
                        "candidateSymbols must not be empty when manual backtest is enabled."
                );
            }
            Set<String> uniqueSymbols = new HashSet<>();
            for (String symbol : candidateSymbols) {
                if (symbol.isBlank()) {
                    throw new IllegalArgumentException(
                            "candidateSymbols must not contain blank symbols."
                    );
                }
                if (!uniqueSymbols.add(symbol)) {
                    throw new IllegalArgumentException(
                            "candidateSymbols must not contain duplicate symbol: "
                                    + symbol
                    );
                }
            }
            Objects.requireNonNull(
                    fromSignalDate,
                    "fromSignalDate must not be null."
            );
            Objects.requireNonNull(
                    toSignalDate,
                    "toSignalDate must not be null."
            );
            if (fromSignalDate.isAfter(toSignalDate)) {
                throw new IllegalArgumentException(
                        "fromSignalDate must not be after toSignalDate."
                );
            }
            if (benchmarkId == null || benchmarkId.isBlank()) {
                throw new IllegalArgumentException(
                        "benchmarkId must not be blank."
                );
            }
            if (initialCashAmountKrwPerSymbol == null
                    || initialCashAmountKrwPerSymbol <= 0) {
                throw new IllegalArgumentException(
                        "initialCashAmountKrwPerSymbol must be positive."
                );
            }
            Objects.requireNonNull(costModel, "costModel must not be null.");
        }
    }
}
