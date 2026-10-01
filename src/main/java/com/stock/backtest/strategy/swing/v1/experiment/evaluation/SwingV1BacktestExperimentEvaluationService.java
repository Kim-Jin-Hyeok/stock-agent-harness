package com.stock.backtest.strategy.swing.v1.experiment.evaluation;

import com.stock.agent.evidence.swing.v1.SwingV1DecisionEvidence;
import com.stock.backtest.comparison.buyandhold.calculation.BuyAndHoldBacktestCalculator;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestRequest;
import com.stock.backtest.comparison.buyandhold.model.BuyAndHoldBacktestResult;
import com.stock.backtest.performance.equity.BacktestEquitySnapshot;
import com.stock.backtest.strategy.swing.v1.execution.result.SwingV1BacktestStepResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentResult;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentService;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummary;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummaryCalculator;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReport;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.market.price.lookup.CurrentPriceLookupSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SwingV1BacktestExperimentEvaluationService {
    private final SwingV1BacktestExperimentService experimentService;
    private final SwingV1BacktestExperimentSummaryCalculator summaryCalculator;
    private final DailyPriceHistoryQueryService priceHistoryQueryService;
    private final BuyAndHoldBacktestCalculator buyAndHoldCalculator;

    public SwingV1BacktestExperimentEvaluationService(
            SwingV1BacktestExperimentService experimentService,
            SwingV1BacktestExperimentSummaryCalculator summaryCalculator,
            DailyPriceHistoryQueryService priceHistoryQueryService,
            BuyAndHoldBacktestCalculator buyAndHoldCalculator
    ) {
        this.experimentService = Objects.requireNonNull(
                experimentService,
                "experimentService must not be null."
        );
        this.summaryCalculator = Objects.requireNonNull(
                summaryCalculator,
                "summaryCalculator must not be null."
        );
        this.priceHistoryQueryService = Objects.requireNonNull(
                priceHistoryQueryService,
                "priceHistoryQueryService must not be null."
        );
        this.buyAndHoldCalculator = Objects.requireNonNull(
                buyAndHoldCalculator,
                "buyAndHoldCalculator must not be null."
        );
    }

    public SwingV1BacktestExperimentEvaluation evaluate(
            SwingV1BacktestExperimentRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        SwingV1BacktestExperimentResult result = experimentService.execute(
                request
        );
        SwingV1BacktestExperimentSummary summary = summaryCalculator.calculate(
                result
        );
        List<BuyAndHoldBacktestResult> comparisons = new ArrayList<>();
        for (SwingV1BacktestReport report : result.reports()) {
            List<BacktestEquitySnapshot> curve = report.runResult().equityCurve();
            if (curve.isEmpty()) {
                throw new IllegalArgumentException(
                        "Report equityCurve must not be empty."
                );
            }
            String symbol = report.request().candidateSymbol();
            LocalDate fromDate = curve.getFirst().valuationDate();
            LocalDate toDate = curve.getLast().valuationDate();
            DailyPriceHistory history = priceHistoryQueryService
                    .getDailyPriceHistory(new DailyPriceHistoryRequest(
                            symbol,
                            fromDate,
                            toDate
                    ));
            validateSameOpenPrices(report, history);
            List<Instant> instants = curve.stream()
                    .map(BacktestEquitySnapshot::evaluatedAt)
                    .toList();
            for (BigDecimal ratio : SwingV1BacktestExperimentEvaluation
                    .BUY_AND_HOLD_INITIAL_ALLOCATION_RATIOS) {
                comparisons.add(buyAndHoldCalculator.calculate(
                        new BuyAndHoldBacktestRequest(
                                symbol,
                                request.initialCashAmountKrwPerSymbol(),
                                ratio,
                                instants,
                                request.costModel()
                        ),
                        history
                ));
            }
        }
        return new SwingV1BacktestExperimentEvaluation(
                result,
                summary,
                comparisons
        );
    }

    private void validateSameOpenPrices(
            SwingV1BacktestReport report,
            DailyPriceHistory history
    ) {
        List<SwingV1BacktestStepResult> steps = report.runResult().steps().stream()
                .filter(step -> step.equitySnapshot() != null)
                .toList();
        if (!steps.stream().map(step -> step.equitySnapshot().valuationDate())
                .toList().equals(history.bars().stream()
                        .map(bar -> bar.tradingDate()).toList())) {
            throw new IllegalArgumentException(
                    "History trading dates must exactly match valuation dates."
            );
        }
        for (int index = 0; index < steps.size(); index++) {
            SwingV1BacktestStepResult step = steps.get(index);
            SwingV1DecisionEvidence evidence = Objects.requireNonNull(
                    step.decision().swingV1Evidence(),
                    "SWING comparison requires daily-open decision evidence."
            );
            if (evidence.currentPriceSource() != CurrentPriceLookupSource.BACKTEST_DAILY_OPEN
                    || !history.symbol().equals(evidence.currentPrice().symbol())
                    || evidence.currentPrice().priceKrw()
                    != history.bars().get(index).openPriceKrw()
                    || !step.equitySnapshot().evaluatedAt().equals(
                            evidence.currentPrice().observedAt()
                    )) {
                throw new IllegalStateException(
                        "Comparison opening prices must match SWING decision evidence. "
                                + "symbol=" + history.symbol()
                                + ", valuationDate=" + step.equitySnapshot().valuationDate()
                );
            }
        }
    }
}
