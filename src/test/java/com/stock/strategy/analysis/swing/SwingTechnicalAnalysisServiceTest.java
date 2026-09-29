package com.stock.strategy.analysis.swing;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.analysis.movingaverage.MovingAverageAnalysisService;
import com.stock.strategy.analysis.volatility.atr.AverageTrueRangeAnalysisService;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverageCalculator;
import com.stock.strategy.indicator.movingaverage.config.ConfiguredStrategyMovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.StrategyMovingAverageProperties;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.indicator.volatility.atr.WilderAverageTrueRangeCalculator;
import com.stock.strategy.indicator.volatility.atr.config.ConfiguredStrategyAverageTrueRangePeriod;
import com.stock.strategy.indicator.volatility.atr.config.StrategyAverageTrueRangeProperties;
import com.stock.strategy.indicator.volatility.atr.policy.StrategyAverageTrueRangePeriodPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignalEvaluator;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SwingTechnicalAnalysisServiceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "SWING_V1",
                    1,
                    InvestmentHorizon.SWING
            );

    private final SwingTechnicalAnalysisService service = service();

    @Test
    void analyzesMovingAverageAndAverageTrueRangeFromSameHistory() {
        SwingTechnicalAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                history(4)
        );

        assertThat(result.status())
                .isEqualTo(SwingTechnicalAnalysisStatus.ANALYZED);
        assertThat(result.requiredBarCount()).isEqualTo(4);
        assertThat(result.availableBarCount()).isEqualTo(4);
        assertThat(result.movingAverageAnalysis().indicator()
                .shortMovingAverage().period()).isEqualTo(2);
        assertThat(result.movingAverageAnalysis().indicator()
                .longMovingAverage().period()).isEqualTo(3);
        assertThat(result.averageTrueRangeAnalysis().averageTrueRange()
                .period()).isEqualTo(2);
    }

    @Test
    void returnsInsufficientDataWhenOneAnalysisNeedsMoreBars() {
        SwingTechnicalAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                history(3)
        );

        assertThat(result.status())
                .isEqualTo(SwingTechnicalAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.requiredBarCount()).isEqualTo(4);
        assertThat(result.availableBarCount()).isEqualTo(3);
        assertThat(result.movingAverageAnalysis().indicator()).isNull();
        assertThat(result.averageTrueRangeAnalysis().averageTrueRange())
                .isNotNull();
    }

    @Test
    void rejectsNonSwingStrategy() {
        InvestmentStrategyIdentity dayTrading =
                new InvestmentStrategyIdentity(
                        "DAY_TRADING_V1",
                        1,
                        InvestmentHorizon.DAY_TRADING
                );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.analyze(dayTrading, history(4)))
                .withMessage("strategyIdentity horizon must be SWING.");
    }

    private SwingTechnicalAnalysisService service() {
        StrategyDailyPriceHistoryPolicy historyPolicy = historyPolicy();
        MovingAverageAnalysisService movingAverageService =
                new MovingAverageAnalysisService(
                        new StrategyMovingAveragePeriodPolicy(
                                new StrategyMovingAverageProperties(List.of(
                                        movingAveragePeriods()
                                )),
                                historyPolicy
                        ),
                        new MovingAverageIndicatorCalculator(
                                new SimpleMovingAverageCalculator()
                        ),
                        new MovingAverageTrendEvaluator(),
                        new MovingAverageCrossoverSignalEvaluator()
                );
        AverageTrueRangeAnalysisService averageTrueRangeService =
                new AverageTrueRangeAnalysisService(
                        new StrategyAverageTrueRangePeriodPolicy(
                                new StrategyAverageTrueRangeProperties(List.of(
                                        averageTrueRangePeriod()
                                )),
                                historyPolicy
                        ),
                        new WilderAverageTrueRangeCalculator()
                );
        return new SwingTechnicalAnalysisService(
                movingAverageService,
                averageTrueRangeService
        );
    }

    private StrategyDailyPriceHistoryPolicy historyPolicy() {
        return new StrategyDailyPriceHistoryPolicy(
                new StrategyDailyPriceHistoryProperties(List.of(
                        new ConfiguredStrategyDailyPriceHistoryLimit(
                                STRATEGY_IDENTITY.strategyId(),
                                STRATEGY_IDENTITY.strategyVersion(),
                                STRATEGY_IDENTITY.horizon(),
                                4
                        )
                ))
        );
    }

    private ConfiguredStrategyMovingAveragePeriods movingAveragePeriods() {
        return new ConfiguredStrategyMovingAveragePeriods(
                STRATEGY_IDENTITY.strategyId(),
                STRATEGY_IDENTITY.strategyVersion(),
                STRATEGY_IDENTITY.horizon(),
                2,
                3
        );
    }

    private ConfiguredStrategyAverageTrueRangePeriod
            averageTrueRangePeriod() {
        return new ConfiguredStrategyAverageTrueRangePeriod(
                STRATEGY_IDENTITY.strategyId(),
                STRATEGY_IDENTITY.strategyVersion(),
                STRATEGY_IDENTITY.horizon(),
                2
        );
    }

    private DailyPriceHistory history(int size) {
        List<DailyPriceBar> bars = IntStream
                .rangeClosed(1, size)
                .mapToObj(index -> {
                    long closePriceKrw = 100L + index * 10L;
                    return new DailyPriceBar(
                            LocalDate.of(2026, 9, index),
                            closePriceKrw,
                            closePriceKrw,
                            closePriceKrw,
                            closePriceKrw,
                            1_000L
                    );
                })
                .toList();
        return new DailyPriceHistory("005930", bars);
    }
}
