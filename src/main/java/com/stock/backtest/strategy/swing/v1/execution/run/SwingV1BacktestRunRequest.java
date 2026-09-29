package com.stock.backtest.strategy.swing.v1.execution.run;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;

import java.time.LocalDate;
import java.util.Objects;

public record SwingV1BacktestRunRequest(
        InvestmentStrategyIdentity strategyIdentity,
        String candidateSymbol,
        LocalDate fromSignalDate,
        LocalDate toSignalDate,
        BacktestPortfolioState initialPortfolioState,
        TradeCostModel costModel
) {
    private static final String STRATEGY_ID = "SWING_V1";
    private static final int STRATEGY_VERSION = 1;

    public SwingV1BacktestRunRequest {
        Objects.requireNonNull(
                strategyIdentity,
                "strategyIdentity must not be null."
        );
        validateStrategyIdentity(strategyIdentity);
        if (candidateSymbol == null || candidateSymbol.isBlank()) {
            throw new IllegalArgumentException(
                    "candidateSymbol must not be blank."
            );
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
        Objects.requireNonNull(
                initialPortfolioState,
                "initialPortfolioState must not be null."
        );
        validateSingleSymbolPortfolio(
                initialPortfolioState,
                candidateSymbol
        );
        Objects.requireNonNull(costModel, "costModel must not be null.");
    }

    private static void validateStrategyIdentity(
            InvestmentStrategyIdentity strategyIdentity
    ) {
        if (!STRATEGY_ID.equals(strategyIdentity.strategyId())
                || strategyIdentity.strategyVersion() != STRATEGY_VERSION
                || strategyIdentity.horizon() != InvestmentHorizon.SWING) {
            throw new IllegalArgumentException(
                    "strategyIdentity must be SWING_V1 version 1."
            );
        }
    }

    private static void validateSingleSymbolPortfolio(
            BacktestPortfolioState portfolioState,
            String candidateSymbol
    ) {
        boolean containsOtherSymbol = portfolioState.positions()
                .stream()
                .anyMatch(position -> !candidateSymbol.equals(
                        position.symbol()
                ));
        if (containsOtherSymbol) {
            throw new IllegalArgumentException(
                    "initialPortfolioState positions must match "
                            + "candidateSymbol for single-symbol "
                            + "backtest run."
            );
        }
    }
}
