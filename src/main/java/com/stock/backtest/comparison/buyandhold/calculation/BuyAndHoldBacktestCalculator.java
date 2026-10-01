package com.stock.backtest.comparison.buyandhold.calculation;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestRequest;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestResult;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.performance.metric.BacktestPerformanceSummary;
import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.portfolio.BacktestPosition;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostCalculation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class BuyAndHoldBacktestCalculator {
    private final TradeCostCalculator tradeCostCalculator;
    private final BacktestPerformanceCalculator performanceCalculator;

    public BuyAndHoldBacktestCalculator(
            TradeCostCalculator tradeCostCalculator,
            BacktestPerformanceCalculator performanceCalculator
    ) {
        this.tradeCostCalculator = Objects.requireNonNull(
                tradeCostCalculator,
                "tradeCostCalculator must not be null."
        );
        this.performanceCalculator = Objects.requireNonNull(
                performanceCalculator,
                "performanceCalculator must not be null."
        );
    }

    public BuyAndHoldBacktestResult calculate(
            BuyAndHoldBacktestRequest request,
            DailyPriceHistory history
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(history, "history must not be null.");
        validateHistory(request, history);
        long quantity = affordableQuantity(
                request,
                history.bars().getFirst().openPriceKrw()
        );
        TradeCostCalculation buy = quantity == 0 ? null
                : tradeCostCalculator.calculate(
                        request.costModel(),
                        InvestmentAction.BUY,
                        quantity,
                        history.bars().getFirst().openPriceKrw()
                );
        long cash = buy == null ? request.initialCashAmountKrw()
                : Math.subtractExact(
                        request.initialCashAmountKrw(),
                        buy.settlementAmountKrw()
                );
        BacktestPortfolioState portfolio = buy == null
                ? BacktestPortfolioState.withCash(cash)
                : new BacktestPortfolioState(cash, List.of(
                        new BacktestPosition(
                                request.symbol(),
                                quantity,
                                buy.executionPriceKrw()
                        )
                ));
        List<BacktestEquitySnapshot> curve = new ArrayList<>();
        for (int index = 0; index < history.bars().size(); index++) {
            DailyPriceBar bar = history.bars().get(index);
            long positionValue = Math.multiplyExact(quantity, bar.openPriceKrw());
            curve.add(new BacktestEquitySnapshot(
                    bar.tradingDate(),
                    request.valuationInstants().get(index),
                    cash,
                    positionValue,
                    Math.addExact(cash, positionValue)
            ));
        }
        BacktestPerformanceSummary performance = performanceCalculator.calculate(
                request.initialCashAmountKrw(),
                curve
        );
        TradeCostCalculation terminalSell = quantity == 0 ? null
                : tradeCostCalculator.calculate(
                        request.costModel(),
                        InvestmentAction.SELL,
                        quantity,
                        history.bars().getLast().openPriceKrw()
                );
        long terminalCost = terminalSell == null ? 0L
                : terminalSell.totalCostAmountKrw();
        long adjustedEquity = Math.subtractExact(
                performance.finalEquityAmountKrw(),
                terminalCost
        );
        long adjustedProfit = Math.subtractExact(
                adjustedEquity,
                request.initialCashAmountKrw()
        );
        return new BuyAndHoldBacktestResult(
                request,
                buy,
                portfolio,
                curve,
                performance,
                terminalSell,
                terminalCost,
                adjustedEquity,
                adjustedProfit,
                BigDecimal.valueOf(adjustedProfit).divide(
                        BigDecimal.valueOf(request.initialCashAmountKrw()),
                        MathContext.DECIMAL128
                )
        );
    }

    private void validateHistory(
            BuyAndHoldBacktestRequest request,
            DailyPriceHistory history
    ) {
        if (!request.symbol().equals(history.symbol())) {
            throw new IllegalArgumentException(
                    "history symbol must match request symbol."
            );
        }
        if (!request.valuationDates().equals(history.bars().stream()
                .map(DailyPriceBar::tradingDate)
                .toList())) {
            throw new IllegalArgumentException(
                    "History trading dates must exactly match valuation dates."
            );
        }
    }

    private long affordableQuantity(
            BuyAndHoldBacktestRequest request,
            long referencePriceKrw
    ) {
        long budget = request.buyBudgetAmountKrw();
        long lower = 0L;
        long upper = budget / referencePriceKrw;
        // Settlement is monotonic, including rounded commission and slippage.
        while (lower < upper) {
            long difference = upper - lower;
            long candidate = lower + difference / 2 + difference % 2;
            boolean fits;
            try {
                fits = tradeCostCalculator.calculate(
                        request.costModel(),
                        InvestmentAction.BUY,
                        candidate,
                        referencePriceKrw
                ).settlementAmountKrw() <= budget;
            } catch (ArithmeticException exception) {
                fits = false;
            }
            if (fits) {
                lower = candidate;
            } else {
                upper = candidate - 1;
            }
        }
        return lower;
    }
}
