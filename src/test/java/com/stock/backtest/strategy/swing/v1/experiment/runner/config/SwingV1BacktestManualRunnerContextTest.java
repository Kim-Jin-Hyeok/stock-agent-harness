package com.stock.backtest.strategy.swing.v1.experiment.runner.config;

import com.stock.backtest.strategy.swing.v1.experiment.evaluation.SwingV1BacktestExperimentEvaluationService;
import com.stock.backtest.strategy.swing.v1.experiment.runner.SwingV1BacktestManualRunner;
import com.stock.backtest.strategy.swing.v1.experiment.sensitivity.cost.SwingV1CostSensitivityService;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.strategy.universe.config.StrategyStockUniverseProperties;
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
                    )
                    .withBean(SwingV1CostSensitivityService.class,
                            () -> mock(SwingV1CostSensitivityService.class));

    @Test
    void doesNotRegisterRunnerWhenDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(
                    SwingV1BacktestManualRunner.class
            );
            assertThat(context.getBean(SwingV1BacktestManualRunProperties.class)
                    .candidateSymbols()).isEmpty();
            assertThat(context.getBean(SwingV1BacktestManualRunProperties.class)
                    .slippageSensitivityEnabled()).isFalse();
        });
    }

    @Test
    void bindsCostModelAndRegistersRunnerWhenEnabled() {
        contextRunner.withPropertyValues(
                "backtest.swing-v1.experiment.manual.enabled=true",
                "backtest.swing-v1.experiment.manual.candidate-symbols[0]=000660",
                "backtest.swing-v1.experiment.manual.candidate-symbols[1]=005930",
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
            assertThat(properties.slippageSensitivityEnabled()).isFalse();
            assertThat(properties.candidateSymbols()).containsExactly("000660", "005930");
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
                "backtest.swing-v1.experiment.manual.candidate-symbols[0]=005930",
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

    @Test
    void enablesSlippageSensitivityOnlyWhenExplicitlyConfigured() {
        enabledContextRunner().withPropertyValues(
                "backtest.swing-v1.experiment.manual.candidate-symbols=005930",
                "backtest.swing-v1.experiment.manual.slippage-sensitivity-enabled=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SwingV1BacktestManualRunProperties.class)
                    .slippageSensitivityEnabled()).isTrue();
        });
    }

    @Test
    void failsStartupWhenEnabledWithoutCandidates() {
        enabledContextRunner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "candidateSymbols must not be empty when manual backtest is enabled."
            );
        });
    }

    @Test
    void failsStartupWhenEnabledWithDuplicateCandidates() {
        enabledContextRunner().withPropertyValues(
                "backtest.swing-v1.experiment.manual.candidate-symbols=005930,005930"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "candidateSymbols must not contain duplicate symbol: 005930"
            );
        });
    }

    @Test
    void keepsBacktestCandidatesSeparateFromOperatingUniverse() {
        enabledContextRunner()
                .withUserConfiguration(OperatingUniverseConfiguration.class)
                .withPropertyValues(
                        "backtest.swing-v1.experiment.manual.candidate-symbols=000660,035420",
                        "strategy.universes[0].strategy-id=SWING_V1",
                        "strategy.universes[0].strategy-version=1",
                        "strategy.universes[0].horizon=SWING",
                        "strategy.universes[0].symbols[0]=005930"
                ).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SwingV1BacktestManualRunProperties.class)
                            .candidateSymbols()).containsExactly("000660", "035420");
                    assertThat(context.getBean(StrategyStockUniverseRegistry.class)
                            .getCandidateSymbols(new InvestmentStrategyIdentity(
                                    "SWING_V1", 1, InvestmentHorizon.SWING
                            ))).containsExactly("005930");
                });
    }

    private ApplicationContextRunner enabledContextRunner() {
        return contextRunner.withPropertyValues(
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
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StrategyStockUniverseProperties.class)
    @Import(StrategyStockUniverseRegistry.class)
    static class OperatingUniverseConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SwingV1BacktestManualRunProperties.class)
    @Import(SwingV1BacktestManualRunner.class)
    static class TestConfiguration {
    }
}
