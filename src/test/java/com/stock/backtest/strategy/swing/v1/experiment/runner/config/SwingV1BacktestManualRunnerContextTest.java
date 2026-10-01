package com.stock.backtest.strategy.swing.v1.experiment.runner.config;

import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.runner.SwingV1BacktestManualRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SwingV1BacktestManualRunnerContextTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class)
                    .withBean(
                            SwingV1BacktestExperimentEvaluationService.class,
                            () -> mock(
                                    SwingV1BacktestExperimentEvaluationService.class
                            )
                    );

    @Test
    void doesNotRegisterRunnerWhenDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(
                    SwingV1BacktestManualRunner.class
            );
        });
    }

    @Test
    void bindsCostModelAndRegistersRunnerWhenEnabled() {
        contextRunner.withPropertyValues(
                "backtest.swing-v1.experiment.manual.enabled=true",
                "backtest.swing-v1.experiment.manual.from-signal-date=2026-08-03",
                "backtest.swing-v1.experiment.manual.to-signal-date=2026-08-28",
                "backtest.swing-v1.experiment.manual.benchmark-id=KOSPI",
                "backtest.swing-v1.experiment.manual.initial-cash-amount-krw-per-symbol=10000000",
                "backtest.swing-v1.experiment.manual.cost-model.model-id=TEST_COST_V1",
                "backtest.swing-v1.experiment.manual.cost-model.model-version=1",
                "backtest.swing-v1.experiment.manual.cost-model.buy-commission-rate=0.00015",
                "backtest.swing-v1.experiment.manual.cost-model.sell-commission-rate=0.00015",
                "backtest.swing-v1.experiment.manual.cost-model.sell-tax-rate=0.0018",
                "backtest.swing-v1.experiment.manual.cost-model.buy-slippage-rate=0.001",
                "backtest.swing-v1.experiment.manual.cost-model.sell-slippage-rate=0.001"
        ).run(context -> {
            assertThat(context).hasSingleBean(
                    SwingV1BacktestManualRunner.class
            );
            SwingV1BacktestManualRunProperties properties = context.getBean(
                    SwingV1BacktestManualRunProperties.class
            );
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.costModel().modelId())
                    .isEqualTo("TEST_COST_V1");
            assertThat(properties.costModel().buyCommissionRate())
                    .isEqualByComparingTo("0.00015");
        });
    }

    @Test
    void failsStartupWhenEnabledWithoutCostModel() {
        contextRunner.withPropertyValues(
                "backtest.swing-v1.experiment.manual.enabled=true",
                "backtest.swing-v1.experiment.manual.from-signal-date=2026-08-03",
                "backtest.swing-v1.experiment.manual.to-signal-date=2026-08-28",
                "backtest.swing-v1.experiment.manual.benchmark-id=KOSPI",
                "backtest.swing-v1.experiment.manual.initial-cash-amount-krw-per-symbol=10000000"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("costModel must not be null.");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SwingV1BacktestManualRunProperties.class)
    @Import(SwingV1BacktestManualRunner.class)
    static class TestConfiguration {
    }
}
