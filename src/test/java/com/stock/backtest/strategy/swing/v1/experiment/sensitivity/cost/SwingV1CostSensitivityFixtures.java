package com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost;

import com.stock.agent.decision.swing.v1.SwingV1DecisionService;
import com.stock.agent.decision.swing.v1.policy.SwingV1ActionPolicy;
import com.stock.agent.decision.swing.v1.quantity.policy.SwingV1OrderQuantityPolicy;
import com.stock.agent.decision.swing.v1.resolution.SwingV1DecisionResolver;
import com.stock.backtest.comparison.buyandhold.calculation.BuyAndHoldBacktestCalculator;
import com.stock.backtest.context.portfolio.BacktestPortfolioEvaluationContextFactory;
import com.stock.backtest.execution.fill.daily.DailyOpenFillApproximationService;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkObservation;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkPerformanceCalculator;
import com.stock.backtest.performance.benchmark.BacktestBenchmarkSeries;
import com.stock.backtest.performance.benchmark.query.BacktestBenchmarkSeriesQueryService;
import com.stock.backtest.performance.metric.BacktestPerformanceCalculator;
import com.stock.backtest.portfolio.transition.BacktestPortfolioTransitionService;
import com.stock.backtest.strategy.swing.v1.execution.SwingV1BacktestStepService;
import com.stock.backtest.strategy.swing.v1.execution.run.SwingV1BacktestRunService;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluation;
import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentRequest;
import com.stock.backtest.strategy.swing.v1.experiment.execution.SwingV1BacktestExperimentService;
import com.stock.backtest.strategy.swing.v1.experiment.summary.SwingV1BacktestExperimentSummaryCalculator;
import com.stock.backtest.strategy.swing.v1.report.SwingV1BacktestReportService;
import com.stock.backtest.strategy.swing.v1.report.terminal.SwingV1TerminalLiquidationCalculator;
import com.stock.backtest.strategy.swing.v1.report.trade.SwingV1CompletedTradeExtractor;
import com.stock.backtest.strategy.swing.v1.report.trade.metric.SwingV1TradePerformanceCalculator;
import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.query.DailyPriceHistoryQueryService;
import com.stock.market.price.validation.CurrentPriceFreshnessPolicy;
import com.stock.market.price.validation.CurrentPriceFreshnessProperties;
import com.stock.portfolio.valuation.PortfolioValuationService;
import com.stock.risk.RiskProperties;
import com.stock.risk.capacity.OrderQuantityCapacityCalculator;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.swing.SwingTechnicalAnalysisService;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisService;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.MovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverageCalculator;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.indicator.volatility.atr.WilderAverageTrueRangeCalculator;
import com.stock.strategy.indicator.volatility.atr.policy.StrategyAverageTrueRangePeriodPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignalEvaluator;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import com.stock.trade.cost.TradeCostCalculator;
import com.stock.trade.cost.model.TradeCostModel;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class SwingV1CostSensitivityFixtures {
    static final InvestmentStrategyIdentity IDENTITY =
            new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING);
    static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    final DailyPriceHistoryQueryService queryService = mock(DailyPriceHistoryQueryService.class);
    final SwingV1BacktestExperimentEvaluationService evaluationService;

    SwingV1CostSensitivityFixtures() {
        when(queryService.getDailyPriceHistory(any())).thenAnswer(invocation -> {
            DailyPriceHistoryRequest request = invocation.getArgument(0);
            return new DailyPriceHistory(request.symbol(), bars().stream()
                    .filter(bar -> !bar.tradingDate().isBefore(request.fromDate())
                            && !bar.tradingDate().isAfter(request.toDate())).toList());
        });
        when(queryService.getLatestDailyPriceHistoryAtOrBefore(anyString(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    String symbol = invocation.getArgument(0);
                    LocalDate date = invocation.getArgument(1);
                    int limit = invocation.getArgument(2);
                    List<DailyPriceBar> history = bars().stream()
                            .filter(bar -> !bar.tradingDate().isAfter(date)).toList();
                    return new DailyPriceHistory(symbol,
                            history.subList(Math.max(0, history.size() - limit), history.size()));
                });
        when(queryService.getFirstDailyPriceBarAfter(anyString(), any())).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(1);
            return bars().stream().filter(bar -> bar.tradingDate().isAfter(date)).findFirst();
        });
        StrategyDailyPriceHistoryPolicy historyPolicy = mock(StrategyDailyPriceHistoryPolicy.class);
        when(historyPolicy.getLatestBarCount(IDENTITY)).thenReturn(120);
        StrategyMovingAveragePeriodPolicy movingAveragePolicy = mock(StrategyMovingAveragePeriodPolicy.class);
        when(movingAveragePolicy.getPeriods(IDENTITY)).thenReturn(new MovingAveragePeriods(20, 60));
        StrategyAverageTrueRangePeriodPolicy atrPolicy = mock(StrategyAverageTrueRangePeriodPolicy.class);
        when(atrPolicy.getPeriod(IDENTITY)).thenReturn(14);
        SwingV1DecisionService decisionService = new SwingV1DecisionService(
                new SwingTechnicalAnalysisService(
                        new MovingAverageAnalysisService(movingAveragePolicy,
                                new MovingAverageIndicatorCalculator(new SimpleMovingAverageCalculator()), new MovingAverageTrendEvaluator(),
                                new MovingAverageCrossoverSignalEvaluator()),
                        new AverageTrueRangeAnalysisService(atrPolicy, new WilderAverageTrueRangeCalculator())),
                new SwingV1ActionPolicy(),
                new PortfolioValuationService(new CurrentPriceFreshnessPolicy(
                        new CurrentPriceFreshnessProperties(Duration.ofMinutes(1)), Clock.systemUTC())),
                new SwingV1OrderQuantityPolicy(new OrderQuantityCapacityCalculator(new RiskProperties(0.1, 0.3))),
                new SwingV1DecisionResolver()
        );
        TradeCostCalculator costs = new TradeCostCalculator();
        BacktestPerformanceCalculator performance = new BacktestPerformanceCalculator();
        SwingV1BacktestRunService runService = new SwingV1BacktestRunService(
                queryService, historyPolicy, new SwingV1BacktestStepService(
                        queryService, new BacktestPortfolioEvaluationContextFactory(), decisionService,
                        new DailyOpenFillApproximationService(queryService, costs),
                        new BacktestPortfolioTransitionService()));
        SwingV1BacktestReportService reportService = new SwingV1BacktestReportService(
                runService, performance, new SwingV1TerminalLiquidationCalculator(costs),
                new SwingV1CompletedTradeExtractor(), new SwingV1TradePerformanceCalculator());
        BacktestBenchmarkSeriesQueryService benchmarkQuery = mock(BacktestBenchmarkSeriesQueryService.class);
        when(benchmarkQuery.getSeries(anyString(), any(), any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            LocalDate from = invocation.getArgument(1);
            LocalDate to = invocation.getArgument(2);
            return new BacktestBenchmarkSeries(id, from.datesUntil(to.plusDays(1))
                    .map(date -> new BacktestBenchmarkObservation(date,
                            BigDecimal.valueOf(1_000L + date.toEpochDay() - from.toEpochDay()))).toList());
        });
        evaluationService = new SwingV1BacktestExperimentEvaluationService(
                new SwingV1BacktestExperimentService(reportService, benchmarkQuery,
                        new BacktestBenchmarkPerformanceCalculator()),
                new SwingV1BacktestExperimentSummaryCalculator(), queryService,
                new BuyAndHoldBacktestCalculator(costs, performance));
    }

    SwingV1BacktestExperimentEvaluation evaluate(SwingV1BacktestExperimentRequest request) {
        return evaluationService.evaluate(request);
    }

    static SwingV1BacktestExperimentRequest request() {
        return new SwingV1BacktestExperimentRequest(
                IDENTITY, List.of("000660", "005930"), "KOSPI",
                START_DATE.plusDays(120), START_DATE.plusDays(123), 1_000_000L,
                model("BASE", "0.001", "0.001"));
    }

    static TradeCostModel model(String id, String buySlippage, String sellSlippage) {
        return new TradeCostModel(id, 1, new BigDecimal("0.00015"), new BigDecimal("0.00015"),
                new BigDecimal("0.0018"), new BigDecimal(buySlippage), new BigDecimal(sellSlippage));
    }

    private List<DailyPriceBar> bars() {
        return IntStream.range(0, 125).mapToObj(index -> {
            long price = switch (index) {
                case 120, 121 -> 11_000L;
                case 122 -> 12_000L;
                default -> 10_000L;
            };
            return new DailyPriceBar(START_DATE.plusDays(index), price,
                    price + 100L, price - 100L, price, 1_000_000L);
        }).toList();
    }
}
