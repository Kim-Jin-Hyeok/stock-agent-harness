package com.stock.backtest.strategy.swing.v1.report.trade;

import com.stock.agent.InvestmentAction;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximation;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SwingV1CompletedTradeExtractor {

    public List<SwingV1CompletedTrade> extract(
            List<SwingV1BacktestStepResult> steps
    ) {
        steps = List.copyOf(Objects.requireNonNull(
                steps,
                "steps must not be null."
        ));
        List<SwingV1CompletedTrade> completedTrades = new ArrayList<>();
        DailyOpenFillApproximation openEntryFill = null;

        for (SwingV1BacktestStepResult step : steps) {
            Objects.requireNonNull(step, "step must not be null.");
            if (step.status() != SwingV1BacktestStepStatus.EXECUTED) {
                continue;
            }

            DailyOpenFillApproximation fill = Objects.requireNonNull(
                    step.fill(),
                    "executed step fill must not be null."
            );
            InvestmentAction action = fill
                    .tradeCostCalculation()
                    .action();
            if (action == InvestmentAction.BUY) {
                if (openEntryFill != null) {
                    throw new IllegalArgumentException(
                            "Executed BUY must not occur while an entry is "
                                    + "open."
                    );
                }
                openEntryFill = fill;
                continue;
            }
            if (action == InvestmentAction.SELL) {
                if (openEntryFill == null) {
                    throw new IllegalArgumentException(
                            "Executed SELL requires an open BUY entry."
                    );
                }
                completedTrades.add(SwingV1CompletedTrade.from(
                        openEntryFill,
                        fill
                ));
                openEntryFill = null;
                continue;
            }
            throw new IllegalStateException(
                    "Executed backtest fill must contain BUY or SELL action."
            );
        }

        return List.copyOf(completedTrades);
    }
}
