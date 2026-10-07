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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KisStockBasicInfoAnalysisProperties.class)
    static class PropertiesConfiguration {
    }
}
