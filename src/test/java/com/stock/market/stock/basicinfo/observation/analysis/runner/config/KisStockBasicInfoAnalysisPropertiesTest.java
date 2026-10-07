package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.InvalidPathException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;

class KisStockBasicInfoAnalysisPropertiesTest {
    private static final String PREFIX = "market.stock.basic-info.analysis.manual.";
    private static final UUID COLLECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void absentSettingsDisableAnalysisWithoutDefaultingAnObservationOrMasterBatch() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class))
                    .isEqualTo(new KisStockBasicInfoAnalysisProperties(false, null, null, null));
        });
    }

    @Test
    void defaultApplicationYamlDisablesManualAnalysisWithoutDefaultInputs() throws Exception {
        var source = new YamlPropertySourceLoader().load("default-analysis-settings", new ClassPathResource("application.yml")).getFirst();

        assertThat(source.getProperty(PREFIX + "enabled")).isEqualTo(false);
        assertThat(source.getProperty(PREFIX + "include-market-warnings")).isEqualTo(false);
        assertThat(source.getProperty(PREFIX + "warning-market")).isNull();
        assertThat(source.getProperty(PREFIX + "observation-id")).isNull();
        assertThat(source.getProperty(PREFIX + "observation-root")).isNull();
        assertThat(source.getProperty(PREFIX + "collection-id")).isNull();
    }

    @Test
    void bindsRootContainingSpacesFromCanonicalEnvironmentVariable() {
        // Boot selects its environment mapper only for systemEnvironment-named sources.
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                        "analysis-test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_OBSERVATIONROOT", "unused preserved masters"))))
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "collection-id=" + COLLECTION_ID)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class).observationRoot()).isEqualTo("unused preserved masters");
                });
    }

    @Test
    void disabledPropertiesDoNotValidateOrResolveUnusedInput() {
        assertThat(new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null).enabled()).isFalse();
    }

    @Test
    void bindsExplicitIdRootAndUuidWithoutRequiringAnExistingDirectory() {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17",
                PREFIX + "observation-root=unused preserved masters", PREFIX + "collection-id=" + COLLECTION_ID).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class))
                    .isEqualTo(new KisStockBasicInfoAnalysisProperties(true, 17L, "unused preserved masters", COLLECTION_ID));
        });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void enabledPropertiesRequireAPositiveObservationId(Long id) {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisProperties(true, id, "unused-root", COLLECTION_ID))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("observationId must be positive.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void enabledPropertiesRequireANonblankMasterRoot(String root) {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisProperties(true, 17L, root, COLLECTION_ID))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("observationRoot must not be blank.");
    }

    @Test
    void rejectsAnInvalidPathWithoutCheckingTheFilesystem() {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisProperties(true, 17L, "bad\0path", COLLECTION_ID))
                .isInstanceOf(InvalidPathException.class);
    }

    @Test
    void enabledPropertiesRequireAnExplicitCollectionId() {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisProperties(true, 17L, "unused-root", null))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessage("collectionId must not be null.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"observation-id", "observation-root", "collection-id"})
    void enabledBindingRejectsMissingRequiredInputs(String missing) {
        var properties = new LinkedHashMap<String, String>();
        properties.put("enabled", "true");
        properties.put("observation-id", "17");
        properties.put("observation-root", "unused-root");
        properties.put("collection-id", COLLECTION_ID.toString());
        properties.remove(missing);
        runner.withPropertyValues(properties.entrySet().stream().map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "17,18", "9223372036854775808"})
    void bindingRejectsMalformedOrMultipleObservationIds(String value) {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=" + value,
                PREFIX + "observation-root=unused-root", PREFIX + "collection-id=" + COLLECTION_ID).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "../other-batch", "00000000-0000-0000-0000-000000000001,00000000-0000-0000-0000-000000000002"})
    void bindingRejectsMalformedOrMultipleCollectionIds(String value) {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17",
                PREFIX + "observation-root=unused-root", PREFIX + "collection-id=" + value).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void legacyConstructorKeepsBasicAnalysisAndDoesNotChooseAWarningMarket() {
        var properties = new KisStockBasicInfoAnalysisProperties(true, 17L, "unused-root", COLLECTION_ID);

        assertThat(properties.includeMarketWarnings()).isFalse();
        assertThat(properties.warningMarket()).isNull();
        assertThat(properties.checkFreshness()).isFalse();
        assertThat(properties.evaluatedAt()).isNull();
        assertThat(properties.maxMasterAge()).isNull();
        assertThat(properties.maxBasicInfoAge()).isNull();
        var combined = new KisStockBasicInfoAnalysisProperties(true, 17L, "unused-root", COLLECTION_ID, true, KisStockMasterMarket.KOSDAQ);
        assertThat(combined.checkFreshness()).isFalse();
        assertThat(combined.evaluatedAt()).isNull();
        assertThat(combined.maxMasterAge()).isNull();
        assertThat(combined.maxBasicInfoAge()).isNull();
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void bindsAnExplicitWarningMarketOnlyWhenCombinedAnalysisIsRequested(KisStockMasterMarket market) {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "observation-root=unused-root",
                PREFIX + "collection-id=" + COLLECTION_ID, PREFIX + "include-market-warnings=true", PREFIX + "warning-market=" + market)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class))
                            .isEqualTo(new KisStockBasicInfoAnalysisProperties(true, 17L, "unused-root", COLLECTION_ID, true, market));
                });
    }

    @Test
    void activeCombinedAnalysisRequiresAWarningMarketWithoutDefaultingToKospi() {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisProperties(true, 17L, "unused-root", COLLECTION_ID, true, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("warningMarket must be specified when market warnings are included.");
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "observation-root=unused-root",
                PREFIX + "collection-id=" + COLLECTION_ID, PREFIX + "include-market-warnings=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("warningMarket must be specified when market warnings are included.");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "UNSUPPORTED", "KOSPI,KOSDAQ"})
    void combinedBindingRejectsMissingUnknownOrMultipleWarningMarkets(String value) {
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "observation-root=unused-root",
                PREFIX + "collection-id=" + COLLECTION_ID, PREFIX + "include-market-warnings=true", PREFIX + "warning-market=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledAnalysisDoesNotRequireCombinedInputsOrEnableWarningsFromAMarketAlone() {
        assertThat(new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null, true, null).enabled()).isFalse();
        runner.withPropertyValues(PREFIX + "enabled=false", PREFIX + "include-market-warnings=true")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "observation-root=unused-root",
                PREFIX + "collection-id=" + COLLECTION_ID, PREFIX + "warning-market=KOSDAQ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class).includeMarketWarnings()).isFalse();
                });
    }

    @Test
    void bindsCombinedOptionsFromCanonicalEnvironmentVariables() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                        "combined-analysis-test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_INCLUDEMARKETWARNINGS", "true",
                                "MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_WARNINGMARKET", "KOSDAQ"))))
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "observation-root=unused-root",
                        PREFIX + "collection-id=" + COLLECTION_ID).run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(KisStockBasicInfoAnalysisProperties.class);
                    assertThat(properties.includeMarketWarnings()).isTrue();
                    assertThat(properties.warningMarket()).isEqualTo(KisStockMasterMarket.KOSDAQ);
                });
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void bindsExplicitFreshnessTimeAndIndependentNanosecondAges(KisStockMasterMarket market) {
        var values = freshnessSettings();
        values.put("warning-market", market.name());
        values.put("max-master-age", "PT24H0.000000011S");
        values.put("max-basic-info-age", "PT1H0.000000019S");

        withSettings(values).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class)).isEqualTo(new KisStockBasicInfoAnalysisProperties(
                    true, 17L, "unused-root", COLLECTION_ID, true, market, true, EVALUATED_AT,
                    MAX_MASTER_AGE.plusNanos(11), MAX_BASIC_INFO_AGE.plusNanos(19)));
        });
    }

    @Test
    void activeFreshnessRequiresCombinedAnalysisWithoutSilentlyEnablingIt() {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisProperties(true, 17L, "unused-root", COLLECTION_ID,
                false, null, true, EVALUATED_AT, MAX_MASTER_AGE, MAX_BASIC_INFO_AGE))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("includeMarketWarnings must be enabled when freshness is checked.");
        var values = freshnessSettings();
        values.put("include-market-warnings", "false");
        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("includeMarketWarnings must be enabled when freshness is checked.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"evaluated-at", "max-master-age", "max-basic-info-age"})
    void activeFreshnessRejectsMissingTimingInputsInsteadOfDefaultingThem(String missing) {
        var values = freshnessSettings();
        values.remove(missing);

        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(NullPointerException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"evaluated-at=not-an-instant", "evaluated-at=2026-10-07T10:00:00", "max-master-age=not-a-duration",
            "max-basic-info-age=not-a-duration", "max-master-age=0s", "max-master-age=-1ns", "max-basic-info-age=0s", "max-basic-info-age=-1ns"})
    void rejectsMalformedTimesAndInvalidDurationsAtBinding(String setting) {
        var values = freshnessSettings();
        var parts = setting.split("=", 2);
        values.put(parts[0], parts[1]);

        withSettings(values).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledAnalysisAndDisabledFreshnessDoNotValidateUnusedTimingOrInferAnOptIn() {
        assertThat(new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null,
                false, null, true, null, Duration.ZERO, Duration.ofNanos(-1)).enabled()).isFalse();
        var values = freshnessSettings();
        values.put("enabled", "false");
        values.put("include-market-warnings", "false");
        values.remove("evaluated-at");
        values.put("max-master-age", "0s");
        withSettings(values).run(context -> assertThat(context).hasNotFailed());
        values.put("enabled", "true");
        values.put("check-freshness", "false");
        withSettings(values).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(KisStockBasicInfoAnalysisProperties.class);
            assertThat(properties.checkFreshness()).isFalse();
            assertThat(properties.includeMarketWarnings()).isFalse();
            assertThat(properties.evaluatedAt()).isNull();
        });
    }

    @Test
    void bindsFreshnessOptionsFromCanonicalEnvironmentVariables() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                        "freshness-analysis-test-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_CHECKFRESHNESS", "true",
                                "MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_EVALUATEDAT", EVALUATED_AT.toString(),
                                "MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_MAXMASTERAGE", "24h",
                                "MARKET_STOCK_BASICINFO_ANALYSIS_MANUAL_MAXBASICINFOAGE", "1h"))))
                .withPropertyValues(PREFIX + "enabled=true", PREFIX + "observation-id=17", PREFIX + "observation-root=unused-root",
                        PREFIX + "collection-id=" + COLLECTION_ID, PREFIX + "include-market-warnings=true", PREFIX + "warning-market=KOSDAQ")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(KisStockBasicInfoAnalysisProperties.class);
                    assertThat(properties.checkFreshness()).isTrue();
                    assertThat(properties.evaluatedAt()).isEqualTo(EVALUATED_AT);
                    assertThat(properties.maxMasterAge()).isEqualTo(MAX_MASTER_AGE);
                    assertThat(properties.maxBasicInfoAge()).isEqualTo(MAX_BASIC_INFO_AGE);
                });
    }

    private Map<String, String> freshnessSettings() {
        var values = new LinkedHashMap<String, String>();
        values.put("enabled", "true");
        values.put("observation-id", "17");
        values.put("observation-root", "unused-root");
        values.put("collection-id", COLLECTION_ID.toString());
        values.put("include-market-warnings", "true");
        values.put("warning-market", "KOSDAQ");
        values.put("check-freshness", "true");
        values.put("evaluated-at", EVALUATED_AT.toString());
        values.put("max-master-age", MAX_MASTER_AGE.toString());
        values.put("max-basic-info-age", MAX_BASIC_INFO_AGE.toString());
        return values;
    }

    private ApplicationContextRunner withSettings(Map<String, String> values) {
        return runner.withPropertyValues(values.entrySet().stream().map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KisStockBasicInfoAnalysisProperties.class)
    static class PropertiesConfiguration {
    }
}
