package com.stock.backtest.strategy.swing.v1.execution.run;

import com.stock.backtest.portfolio.BacktestPortfolioState;
import com.stock.backtest.strategy.swing.v1.execution.SwingV1BacktestStepRequest;
import com.stock.backtest.strategy.swing.v1.execution.SwingV1BacktestStepService;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepStatus;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SwingV1BacktestRunService {
    private final DailyPriceHistoryQueryService priceHistoryQueryService;
    private final StrategyDailyPriceHistoryPolicy priceHistoryPolicy;
    private final SwingV1BacktestStepService stepService;

    public SwingV1BacktestRunService(
            DailyPriceHistoryQueryService priceHistoryQueryService,
            StrategyDailyPriceHistoryPolicy priceHistoryPolicy,
            SwingV1BacktestStepService stepService
    ) {
        this.priceHistoryQueryService = Objects.requireNonNull(
                priceHistoryQueryService,
                "priceHistoryQueryService must not be null."
        );
        this.priceHistoryPolicy = Objects.requireNonNull(
                priceHistoryPolicy,
                "priceHistoryPolicy must not be null."
        );
        this.stepService = Objects.requireNonNull(
                stepService,
                "stepService must not be null."
        );
    }

    public SwingV1BacktestRunResult execute(
            SwingV1BacktestRunRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");

        DailyPriceHistory signalHistory = priceHistoryQueryService
                .getDailyPriceHistory(new DailyPriceHistoryRequest(
                        request.candidateSymbol(),
                        request.fromSignalDate(),
                        request.toSignalDate()
                ));
        int historyLimit = priceHistoryPolicy.getLatestBarCount(
                request.strategyIdentity()
        );
        BacktestPortfolioState portfolioState =
                request.initialPortfolioState();
        List<SwingV1BacktestStepResult> steps = new ArrayList<>();

        for (DailyPriceBar signalBar : signalHistory.bars()) {
            DailyPriceHistory decisionHistory = priceHistoryQueryService
                    .getLatestDailyPriceHistoryAtOrBefore(
                            request.candidateSymbol(),
                            signalBar.tradingDate(),
                            historyLimit
                    );
            SwingV1BacktestStepResult step = stepService.execute(
                    new SwingV1BacktestStepRequest(
                            request.strategyIdentity(),
                            request.candidateSymbol(),
                            signalBar.tradingDate(),
                            decisionHistory,
                            portfolioState,
                            request.costModel()
                    )
            );
            steps.add(step);
            portfolioState = step.portfolioStateAfter();

            if (step.status()
                    == SwingV1BacktestStepStatus.NO_NEXT_DAILY_BAR) {
                break;
            }
        }

        return new SwingV1BacktestRunResult(
                request.strategyIdentity(),
                request.candidateSymbol(),
                request.fromSignalDate(),
                request.toSignalDate(),
                request.initialPortfolioState(),
                portfolioState,
                steps
        );
    }
}
