package com.stock.backtest.strategy.swing.v1.execution;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.trade.cost.model.TradeCostModel;

import java.time.LocalDate;
import java.util.Objects;

public record SwingV1BacktestStepRequest(
        InvestmentStrategyIdentity strategyIdentity,
        String candidateSymbol,
        LocalDate signalDate,
        DailyPriceHistory dailyPriceHistory,
        BacktestPortfolioState portfolioState,
        TradeCostModel costModel
) {
    private static final String STRATEGY_ID = "SWING_V1";
    private static final int STRATEGY_VERSION = 1;

    public SwingV1BacktestStepRequest {
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
                signalDate,
                "signalDate must not be null."
        );
        Objects.requireNonNull(
                dailyPriceHistory,
                "dailyPriceHistory must not be null."
        );
        if (!candidateSymbol.equals(dailyPriceHistory.symbol())) {
            throw new IllegalArgumentException(
                    "candidateSymbol must match dailyPriceHistory symbol."
            );
        }
        validateDailyPriceHistory(dailyPriceHistory, signalDate);
        Objects.requireNonNull(
                portfolioState,
                "portfolioState must not be null."
        );
        validateSingleSymbolPortfolio(portfolioState, candidateSymbol);
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

    private static void validateDailyPriceHistory(
            DailyPriceHistory dailyPriceHistory,
            LocalDate signalDate
    ) {
        if (dailyPriceHistory.bars().isEmpty()) {
            throw new IllegalArgumentException(
                    "dailyPriceHistory must contain signalDate bar."
            );
        }
        DailyPriceBar latestBar = dailyPriceHistory.bars()
                .getLast();
        if (!latestBar.tradingDate().equals(signalDate)) {
            throw new IllegalArgumentException(
                    "dailyPriceHistory latest tradingDate must match "
                            + "signalDate."
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
                    "portfolioState positions must match candidateSymbol "
                            + "for single-symbol backtest step."
            );
        }
    }
}
