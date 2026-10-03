package com.stock.strategy.universe.liquidity.evaluation.runner.config;

import com.stock.market.price.history.TradingVenueScope;
import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.strategy.universe.config.StrategyStockUniverseProperties;
import com.stock.strategy.universe.liquidity.evaluation.query.DailyTradingValueSelectionQueryService;
import com.stock.strategy.universe.liquidity.evaluation.runner.DailyTradingValueSelectionEvaluationRunner;
import com.stock.strategy.universe.liquidity.evaluation.snapshot.storage.DailyTradingValueSelectionSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.SELECTION_DATE;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.TRADING_DATES;
import static com.stock.strategy.universe.liquidity.evaluation.snapshot.support.DailyTradingValueSelectionSnapshotFixture.completeSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DailyTradingValueSelectionEvaluationRunnerContextTest {
    private static final String PREFIX = "strategy.universe.liquidity.evaluation.manual.";
    private final DailyTradingValueSelectionQueryService queryService = mock(DailyTradingValueSelectionQueryService.class);
    private final DailyTradingValueSelectionSnapshotStore snapshotStore = mock(DailyTradingValueSelectionSnapshotStore.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RunnerConfiguration.class)
            .withBean(DailyTradingValueSelectionQueryService.class, () -> queryService)
            .withBean(DailyTradingValueSelectionSnapshotStore.class, () -> snapshotStore);

    @Test
    void doesNotRegisterRunnerOrRequireInputsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DailyTradingValueSelectionEvaluationRunner.class);
            var properties = context.getBean(DailyTradingValueSelectionEvaluationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.targetSymbols()).isEmpty();
            assertThat(properties.requiredTradingDates()).isEmpty();
            assertThat(properties.minimumAverageTradingValueKrw()).isNull();
            assertThat(properties.maxCandidateCount()).isNull();
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void doesNotRegisterRunnerWhenExplicitlyDisabled() {
        contextRunner.withPropertyValues(PREFIX + "enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DailyTradingValueSelectionEvaluationRunner.class);
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void bindsAllCriteriaAndRegistersRunnerOnlyWhenEnabled() {
        enabledContextRunnerWithout(null).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DailyTradingValueSelectionEvaluationRunner.class);
            var properties = context.getBean(DailyTradingValueSelectionEvaluationProperties.class);
            var snapshot = completeSnapshot();
            assertThat(properties.toRequest()).isEqualTo(snapshot.evaluationResult().request());
            assertThat(properties.targetSymbols()).containsExactly("000660", "005380", "005930", "035420");
            assertThat(properties.selectionAsOfDate()).isEqualTo(SELECTION_DATE);
            assertThat(properties.requiredTradingDates()).isEqualTo(TRADING_DATES);
            assertThat(properties.expectedVenueScope()).isEqualTo(TradingVenueScope.INTEGRATED);
            verifyNoInteractions(queryService, snapshotStore);
            when(queryService.evaluate(properties.toRequest())).thenReturn(snapshot.evaluationResult());
            when(snapshotStore.save(snapshot)).thenReturn(17L);

            context.getBean(DailyTradingValueSelectionEvaluationRunner.class).run(new DefaultApplicationArguments());

            verify(queryService).evaluate(properties.toRequest());
            verify(snapshotStore).save(snapshot);
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "target-symbols;targetSymbols must not be empty.",
            "selection-as-of-date;selectionAsOfDate must not be null.",
            "required-trading-dates;tradingDates must not be empty.",
            "expected-venue-scope;expectedVenueScope must not be null.",
            "minimum-average-trading-value-krw;minimumAverageTradingValueKrw must not be null.",
            "max-candidate-count;maxCandidateCount must not be null."
    }, delimiter = ';')
    void rejectsMissingRequiredPropertyBeforeAnyEvaluation(String missing, String message) {
        enabledContextRunnerWithout(missing).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(message);
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "minimum-average-trading-value-krw=0", "max-candidate-count=0",
            "target-symbols=005930,005930", "required-trading-dates=2026-09-23,2026-09-22",
            "expected-venue-scope=UNKNOWN"
    })
    void rejectsMalformedBoundCriteriaWithoutCallingServices(String invalidProperty) {
        enabledContextRunnerWithout(null).withPropertyValues(PREFIX + invalidProperty).run(context -> {
            assertThat(context).hasFailed();
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void doesNotReplaceConfiguredOperatingUniverseWithEvaluationTargets() {
        enabledContextRunnerWithout(null).withUserConfiguration(OperatingUniverseConfiguration.class)
                .withPropertyValues(
                        "strategy.universes[0].strategy-id=SWING_V1",
                        "strategy.universes[0].strategy-version=1",
                        "strategy.universes[0].horizon=SWING",
                        "strategy.universes[0].symbols=005930"
                ).run(context -> {
                    assertThat(context).hasNotFailed();
                    var registry = context.getBean(StrategyStockUniverseRegistry.class);
                    assertThat(registry.getCandidateSymbols(new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING)))
                            .containsExactly("005930");
                    assertThat(context.getBean(DailyTradingValueSelectionEvaluationProperties.class).targetSymbols()).hasSize(4);
                });
    }

    private ApplicationContextRunner enabledContextRunnerWithout(String missingProperty) {
        Map<String, String> settings = Map.of(
                "enabled", "true",
                "target-symbols", "035420,005930,000660,005380",
                "selection-as-of-date", "2026-09-23",
                "required-trading-dates", "2026-09-21,2026-09-22,2026-09-23",
                "expected-venue-scope", "INTEGRATED",
                "minimum-average-trading-value-krw", "100",
                "max-candidate-count", "2"
        );
        String[] values = settings.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(missingProperty))
                .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
        return contextRunner.withPropertyValues(values);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DailyTradingValueSelectionEvaluationProperties.class)
    @Import(DailyTradingValueSelectionEvaluationRunner.class)
    static class RunnerConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StrategyStockUniverseProperties.class)
    @Import(StrategyStockUniverseRegistry.class)
    static class OperatingUniverseConfiguration {
    }
}
