package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KisStockBasicInfoAnalysisProperties.class)
    static class PropertiesConfiguration {
    }
}
