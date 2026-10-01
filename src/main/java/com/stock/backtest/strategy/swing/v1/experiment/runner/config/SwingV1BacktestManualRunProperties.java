package com.stock.backtest.strategy.swing.v1.experiment.runner.config;

import com.stock.trade.cost.model.TradeCostModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.Objects;

@ConfigurationProperties(prefix = "backtest.swing-v1.experiment.manual")
public record SwingV1BacktestManualRunProperties(
        boolean enabled,
        LocalDate fromSignalDate,
        LocalDate toSignalDate,
        String benchmarkId,
        Long initialCashAmountKrwPerSymbol,
        TradeCostModel costModel
) {
    public SwingV1BacktestManualRunProperties {
        if (enabled) {
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
