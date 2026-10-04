package com.stock.strategy.universe.candidate.evaluation.runner.config;

import com.stock.strategy.profile.InvestmentHorizon;
import com.stock.strategy.profile.InvestmentStrategyIdentity;
import com.stock.strategy.universe.StrategyStockUniverseRegistry;
import com.stock.strategy.universe.candidate.evaluation.query.StockCandidateEvaluationQueryService;
import com.stock.strategy.universe.candidate.evaluation.runner.StockCandidateEvaluationRunner;
import com.stock.strategy.universe.candidate.evaluation.snapshot.storage.StockCandidateEvaluationSnapshotStore;
import com.stock.strategy.universe.config.StrategyStockUniverseProperties;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.stock.strategy.universe.candidate.evaluation.runner.support.StockCandidateEvaluationRunnerFixture.properties;
import static com.stock.strategy.universe.candidate.evaluation.snapshot.support.StockCandidateEvaluationSnapshotFixture.completeSnapshot;
import static com.stock.strategy.universe.candidate.evaluation.support.StockCandidateEvaluationFixture.CUTOFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockCandidateEvaluationRunnerContextTest {
    private static final String PREFIX = "strategy.universe.candidate.evaluation.manual.";
    private final StockCandidateEvaluationQueryService queryService = mock(StockCandidateEvaluationQueryService.class);
    private final StockCandidateEvaluationSnapshotStore snapshotStore = mock(StockCandidateEvaluationSnapshotStore.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RunnerConfiguration.class)
            .withBean(StockCandidateEvaluationQueryService.class, () -> queryService)
            .withBean(StockCandidateEvaluationSnapshotStore.class, () -> snapshotStore);

    @Test
    void doesNotRegisterRunnerOrRequireInputsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(StockCandidateEvaluationRunner.class);
            var properties = context.getBean(StockCandidateEvaluationProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.targetSymbols()).isEmpty();
            assertThat(properties.eligibleMarkets()).isEmpty();
            assertThat(properties.eligibleSecurityTypes()).isEmpty();
            assertThat(properties.eligibilityInputs()).isEmpty();
            assertThat(properties.selectionCutoffAt()).isNull();
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void doesNotRegisterRunnerWhenExplicitlyDisabledOrOnlyLiquidityRunnerIsEnabled() {
        contextRunner.withPropertyValues(PREFIX + "enabled=false",
                "strategy.universe.liquidity.evaluation.manual.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(StockCandidateEvaluationRunner.class);
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void bindsAllExplicitCriteriaAndEligibilityInputsAndRegistersRunnerOnlyWhenEnabled() {
        enabledContextRunnerWithout(null).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StockCandidateEvaluationRunner.class);
            var properties = context.getBean(StockCandidateEvaluationProperties.class);
            var snapshot = completeSnapshot();
            assertThat(properties.toRequest()).isEqualTo(snapshot.evaluationResult().request());
            assertThat(properties.targetSymbols()).containsExactly("000660", "005930", "069500");
            assertThat(properties.selectionCutoffAt()).isEqualTo(CUTOFF);
            assertThat(properties.eligibleMarkets()).containsExactly(StockMarket.KOSPI);
            assertThat(properties.eligibleSecurityTypes()).containsExactly(StockSecurityType.COMMON_STOCK);
            assertThat(properties.eligibilityInputs()).isEqualTo(properties(snapshot).eligibilityInputs());
            assertThat(properties.eligibilityInputs()).extracting(StockEligibilityInput::symbol)
                    .containsExactly("000660", "005930", "069500");
            verifyNoInteractions(queryService, snapshotStore);
            when(queryService.evaluate(properties.toRequest(), properties.eligibilityInputs()))
                    .thenReturn(snapshot.evaluationResult());
            when(snapshotStore.save(snapshot)).thenReturn(17L);

            context.getBean(StockCandidateEvaluationRunner.class).run(new DefaultApplicationArguments());

            verify(queryService).evaluate(properties.toRequest(), properties.eligibilityInputs());
            verify(snapshotStore).save(snapshot);
        });
    }

    @Test
    void acceptsNoEligibilityInputsRatherThanFillingVerifiedMetadata() {
        var values = settings();
        values.keySet().removeIf(key -> key.startsWith("eligibility-inputs["));
        withSettings(values).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StockCandidateEvaluationRunner.class);
            var properties = context.getBean(StockCandidateEvaluationProperties.class);
            assertThat(properties.eligibilityInputs()).isEmpty();
            assertThat(properties.targetSymbols()).hasSize(3);
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void bindsSymbolOnlyEligibilityWithoutDefaultEvidenceDateOrAvailability() {
        var values = settings();
        values.keySet().removeIf(key -> key.startsWith("eligibility-inputs["));
        values.put("eligibility-inputs[0].symbol", "005930");
        withSettings(values).run(context -> {
            assertThat(context).hasNotFailed();
            var input = context.getBean(StockCandidateEvaluationProperties.class).eligibilityInputs().getFirst();
            assertThat(input.symbol()).isEqualTo("005930");
            assertThat(input.asOfDate()).isNull();
            assertThat(input.market()).isNull();
            assertThat(input.securityType()).isNull();
            assertThat(input.listingStatus()).isNull();
            assertThat(input.sourceReference()).isNull();
            assertThat(input.informationAvailableAt()).isNull();
            assertThat(input.evidenceStatus()).isNull();
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "target-symbols;targetSymbols must not be empty.",
            "selection-as-of-date;selectionAsOfDate must not be null.",
            "selection-cutoff-at;selectionCutoffAt must not be null.",
            "eligible-markets;eligibleMarkets must not be empty.",
            "eligible-security-types;eligibleSecurityTypes must not be empty.",
            "required-trading-dates;tradingDates must not be empty.",
            "expected-venue-scope;expectedVenueScope must not be null.",
            "minimum-average-trading-value-krw;minimumAverageTradingValueKrw must not be null.",
            "max-candidate-count;maxCandidateCount must not be null."
    }, delimiter = ';')
    void rejectsMissingEvaluationCriteriaBeforeAnyServiceCall(String missing, String message) {
        enabledContextRunnerWithout(missing).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(message);
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "minimum-average-trading-value-krw=0", "max-candidate-count=0", "target-symbols=005930,005930",
            "required-trading-dates=2026-09-23,2026-09-22", "selection-cutoff-at=2026-09-22T09:00:00Z",
            "eligible-markets=OTHER", "eligible-security-types=ETF", "expected-venue-scope=UNKNOWN",
            "eligibility-inputs[0].symbol=035420", "eligibility-inputs[1].symbol=000660",
            "eligibility-inputs[0].evidence-status=UNKNOWN", "eligibility-inputs[0].information-available-at=not-a-time"
    })
    void rejectsMalformedBoundCriteriaOrInputStructureWithoutCallingServices(String invalidProperty) {
        enabledContextRunnerWithout(null).withPropertyValues(PREFIX + invalidProperty).run(context -> {
            assertThat(context).hasFailed();
            verifyNoInteractions(queryService, snapshotStore);
        });
    }

    @Test
    void doesNotChangeOperatingUniverseOrEnableCandidateExecutionByBindingEvaluationTargets() {
        enabledContextRunnerWithout(null).withUserConfiguration(OperatingUniverseConfiguration.class)
                .withPropertyValues("strategy.universes[0].strategy-id=SWING_V1",
                        "strategy.universes[0].strategy-version=1", "strategy.universes[0].horizon=SWING",
                        "strategy.universes[0].symbols=005930").run(context -> {
                    assertThat(context).hasNotFailed();
                    var registry = context.getBean(StrategyStockUniverseRegistry.class);
                    assertThat(registry.getCandidateSymbols(new InvestmentStrategyIdentity("SWING_V1", 1, InvestmentHorizon.SWING)))
                            .containsExactly("005930");
                    assertThat(context.getBean(StockCandidateEvaluationProperties.class).targetSymbols()).hasSize(3);
                    verifyNoInteractions(queryService, snapshotStore);
                });
    }

    private ApplicationContextRunner enabledContextRunnerWithout(String missingProperty) {
        var values = settings();
        values.remove(missingProperty);
        return withSettings(values);
    }

    private ApplicationContextRunner withSettings(Map<String, String> values) {
        return contextRunner.withPropertyValues(values.entrySet().stream()
                .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    private static Map<String, String> settings() {
        var values = new LinkedHashMap<String, String>(Map.of(
                "enabled", "true", "target-symbols", "005930,069500,000660",
                "selection-as-of-date", "2026-09-23", "selection-cutoff-at", "2026-09-23T09:00:00Z",
                "eligible-markets", "KOSPI", "eligible-security-types", "COMMON_STOCK",
                "required-trading-dates", "2026-09-21,2026-09-22,2026-09-23", "expected-venue-scope", "INTEGRATED",
                "minimum-average-trading-value-krw", "100", "max-candidate-count", "2"
        ));
        var inputs = properties(completeSnapshot()).eligibilityInputs();
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            String prefix = "eligibility-inputs[" + i + "].";
            values.put(prefix + "symbol", input.symbol());
            values.put(prefix + "as-of-date", input.asOfDate().toString());
            values.put(prefix + "market", input.market().name());
            values.put(prefix + "security-type", input.securityType().name());
            values.put(prefix + "listing-status", input.listingStatus().name());
            values.put(prefix + "source-reference", input.sourceReference());
            values.put(prefix + "information-available-at", input.informationAvailableAt().toString());
            values.put(prefix + "evidence-status", input.evidenceStatus().name());
        }
        return values;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StockCandidateEvaluationProperties.class)
    @Import(StockCandidateEvaluationRunner.class)
    static class RunnerConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StrategyStockUniverseProperties.class)
    @Import(StrategyStockUniverseRegistry.class)
    static class OperatingUniverseConfiguration {
    }
}
