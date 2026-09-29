package com.stock.backtest.execution.fill.daily;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.BacktestFillType;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostCalculation;
import com.stock.trade.cost.model.TradeCostModel;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Service
public class DailyOpenFillApproximationService {
    private final DailyPriceHistoryQueryService dailyPriceHistoryQueryService;
    private final TradeCostCalculator tradeCostCalculator;

    public DailyOpenFillApproximationService(
            DailyPriceHistoryQueryService dailyPriceHistoryQueryService,
            TradeCostCalculator tradeCostCalculator
    ) {
        this.dailyPriceHistoryQueryService = Objects.requireNonNull(
                dailyPriceHistoryQueryService,
                "dailyPriceHistoryQueryService must not be null."
        );
        this.tradeCostCalculator = Objects.requireNonNull(
                tradeCostCalculator,
                "tradeCostCalculator must not be null."
        );
    }

    public Optional<DailyOpenFillApproximation> approximate(
            String symbol,
            LocalDate signalDate,
            InvestmentAction action,
            long quantity,
            TradeCostModel costModel
    ) {
        validateRequest(symbol, signalDate, action, quantity, costModel);

        return dailyPriceHistoryQueryService
                .getFirstDailyPriceBarAfter(symbol, signalDate)
                .map(fillBar -> approximate(
                        symbol,
                        signalDate,
                        fillBar,
                        action,
                        quantity,
                        costModel
                ));
    }

    public DailyOpenFillApproximation approximate(
            String symbol,
            LocalDate signalDate,
            DailyPriceBar fillBar,
            InvestmentAction action,
            long quantity,
            TradeCostModel costModel
    ) {
        validateRequest(symbol, signalDate, action, quantity, costModel);
        Objects.requireNonNull(fillBar, "fillBar must not be null.");
        if (!fillBar.tradingDate().isAfter(signalDate)) {
            throw new IllegalArgumentException(
                    "fillBar.tradingDate must be after signalDate."
            );
        }

        TradeCostCalculation tradeCostCalculation =
                tradeCostCalculator.calculate(
                        costModel,
                        action,
                        quantity,
                        fillBar.openPriceKrw()
                );
        return new DailyOpenFillApproximation(
                BacktestFillType.DAILY_OPEN_FILL_APPROXIMATION,
                symbol,
                signalDate,
                fillBar.tradingDate(),
                tradeCostCalculation
        );
    }

    private void validateRequest(
            String symbol,
            LocalDate signalDate,
            InvestmentAction action,
            long quantity,
            TradeCostModel costModel
    ) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank.");
        }
        Objects.requireNonNull(
                signalDate,
                "signalDate must not be null."
        );
        Objects.requireNonNull(action, "action must not be null.");
        if (action == InvestmentAction.HOLD) {
            throw new IllegalArgumentException(
                    "Daily open fill approximation requires BUY or SELL action."
            );
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        Objects.requireNonNull(costModel, "costModel must not be null.");
    }
}
