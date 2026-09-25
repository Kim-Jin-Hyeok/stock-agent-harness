package com.stock.strategy.analysis.movingaverage;

import com.stock.strategy.indicator.movingaverage.MovingAverageIndicatorCalculator;
import com.stock.strategy.indicator.movingaverage.SimpleMovingAverageCalculator;
import com.stock.strategy.indicator.movingaverage.policy.StrategyMovingAveragePeriodPolicy;
import com.stock.strategy.signal.movingaverage.MovingAverageTrendEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MovingAverageAnalysisServiceContextTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            StrategyMovingAveragePeriodPolicy.class,
                            () -> mock(
                                    StrategyMovingAveragePeriodPolicy.class
                            )
                    )
                    .withUserConfiguration(
                            SimpleMovingAverageCalculator.class,
                            MovingAverageIndicatorCalculator.class,
                            MovingAverageTrendEvaluator.class,
                            MovingAverageAnalysisService.class
                    );

    @Test
    void registersMovingAverageAnalysisBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(
                    SimpleMovingAverageCalculator.class
            );
            assertThat(context).hasSingleBean(
                    MovingAverageIndicatorCalculator.class
            );
            assertThat(context).hasSingleBean(
                    MovingAverageTrendEvaluator.class
            );
            assertThat(context).hasSingleBean(
                    MovingAverageAnalysisService.class
            );
        });
    }
}
