package com.stock.strategy.analysis.movingaverage;

import com.stock.market.price.history.DailyPriceBar;
import com.stock.market.price.history.DailyPriceHistory;
import com.stock.strategy.data.history.StrategyDailyPriceHistoryPolicy;
import com.stock.strategy.data.history.config.ConfiguredStrategyDailyPriceHistoryLimit;
import com.stock.strategy.data.history.config.StrategyDailyPriceHistoryProperties;
import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverageCalculator;
import com.stock.strategy.indicator.movingaverage.config.ConfiguredStrategyMovingAveragePeriods;
import com.stock.strategy.indicator.movingaverage.config.StrategyMovingAverageProperties;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignal;
import com.stock.strategy.signal.movingaverage.MovingAverageCrossoverSignalEvaluator;
import com.stock.strategy.signal.movingaverage.MovingAverageTrend;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MovingAverageAnalysisServiceTest {
    private static final InvestmentStrategyIdentity STRATEGY_IDENTITY =
            new InvestmentStrategyIdentity(
                    "DAY_TRADING_V1",
                    1,
                    InvestmentHorizon.DAY_TRADING
            );
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);

    private final MovingAverageAnalysisService service = service();

    @Test
    void analyzesHistoryUsingStrategyPeriods() {
        MovingAverageAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                history(21)
        );

        assertThat(result.status())
                .isEqualTo(MovingAverageAnalysisStatus.ANALYZED);
        assertThat(result.requiredBarCount()).isEqualTo(21);
        assertThat(result.availableBarCount()).isEqualTo(21);
        assertThat(result.previousIndicator().asOfTradingDate())
                .isEqualTo(FIRST_DATE.plusDays(19));
        assertThat(result.previousTrend())
                .isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(result.indicator().shortMovingAverage().period())
                .isEqualTo(5);
        assertThat(result.indicator().longMovingAverage().period())
                .isEqualTo(20);
        assertThat(result.trend()).isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(result.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.NONE);
    }

    @Test
    void returnsInsufficientDataWithBarCounts() {
        MovingAverageAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                history(20)
        );

        assertThat(result.status())
                .isEqualTo(MovingAverageAnalysisStatus.INSUFFICIENT_DATA);
        assertThat(result.requiredBarCount()).isEqualTo(21);
        assertThat(result.availableBarCount()).isEqualTo(20);
        assertThat(result.previousIndicator()).isNull();
        assertThat(result.previousTrend()).isNull();
        assertThat(result.indicator()).isNull();
        assertThat(result.trend()).isNull();
        assertThat(result.crossoverSignal()).isNull();
    }

    @Test
    void returnsGoldenCrossWhenFlatTrendChangesToUptrend() {
        MovingAverageAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                historyWithLatestPrice(200_000L)
        );

        assertThat(result.previousTrend()).isEqualTo(MovingAverageTrend.FLAT);
        assertThat(result.trend()).isEqualTo(MovingAverageTrend.UPTREND);
        assertThat(result.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.GOLDEN_CROSS);
    }

    @Test
    void returnsDeadCrossWhenFlatTrendChangesToDowntrend() {
        MovingAverageAnalysisResult result = service.analyze(
                STRATEGY_IDENTITY,
                historyWithLatestPrice(1_000L)
        );

        assertThat(result.previousTrend()).isEqualTo(MovingAverageTrend.FLAT);
        assertThat(result.trend()).isEqualTo(MovingAverageTrend.DOWNTREND);
        assertThat(result.crossoverSignal())
                .isEqualTo(MovingAverageCrossoverSignal.DEAD_CROSS);
    }

    @Test
    void rejectsUnregisteredStrategyIdentity() {
        InvestmentStrategyIdentity unknown = new InvestmentStrategyIdentity(
                "DAY_TRADING_V2",
                2,
                InvestmentHorizon.DAY_TRADING
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.analyze(unknown, history(21)))
                .withMessage(
                        "Strategy moving average periods not found: "
                                + unknown
                );
    }

    private MovingAverageAnalysisService service() {
        StrategyMovingAveragePeriodPolicy periodPolicy =
                new StrategyMovingAveragePeriodPolicy(
                        movingAverageProperties(),
                        dailyPriceHistoryPolicy()
                );
        SimpleMovingAverageCalculator simpleCalculator =
                new SimpleMovingAverageCalculator();
        return new MovingAverageAnalysisService(
                periodPolicy,
                new MovingAverageIndicatorCalculator(simpleCalculator),
                new MovingAverageTrendEvaluator(),
                new MovingAverageCrossoverSignalEvaluator()
        );
    }

    private StrategyMovingAverageProperties movingAverageProperties() {
        return new StrategyMovingAverageProperties(List.of(
                new ConfiguredStrategyMovingAveragePeriods(
                        STRATEGY_IDENTITY.strategyId(),
                        STRATEGY_IDENTITY.strategyVersion(),
                        STRATEGY_IDENTITY.horizon(),
                        5,
                        20
                )
        ));
    }

    private StrategyDailyPriceHistoryPolicy dailyPriceHistoryPolicy() {
        return new StrategyDailyPriceHistoryPolicy(
                new StrategyDailyPriceHistoryProperties(List.of(
                        new ConfiguredStrategyDailyPriceHistoryLimit(
                                STRATEGY_IDENTITY.strategyId(),
                                STRATEGY_IDENTITY.strategyVersion(),
                                STRATEGY_IDENTITY.horizon(),
                                60
                        )
                ))
        );
    }

    private DailyPriceHistory history(int size) {
        List<DailyPriceBar> bars = IntStream.rangeClosed(1, size)
                .mapToObj(index -> bar(
                        FIRST_DATE.plusDays(index - 1L),
                        index * 1_000L
                ))
                .toList();
        return new DailyPriceHistory("005930", bars);
    }

    private DailyPriceHistory historyWithLatestPrice(long latestPriceKrw) {
        List<DailyPriceBar> bars = IntStream.rangeClosed(1, 21)
                .mapToObj(index -> bar(
                        FIRST_DATE.plusDays(index - 1L),
                        index == 21 ? latestPriceKrw : 100_000L
                ))
                .toList();
        return new DailyPriceHistory("005930", bars);
    }

    private DailyPriceBar bar(
            LocalDate tradingDate,
            long closePriceKrw
    ) {
        return new DailyPriceBar(
                tradingDate,
                closePriceKrw,
                closePriceKrw,
                closePriceKrw,
                closePriceKrw,
                1_000L
        );
    }
}
