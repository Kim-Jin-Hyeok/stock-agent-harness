package com.stock.market.stock.master.collection.runner.config;

import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.market.stock.master.collection.StockMasterCollectionService;
import com.stock.market.stock.master.collection.runner.StockMasterCollectionRunner;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StockMasterCollectionConfigurationTest {
    private static final String PREFIX = "market.stock.master.collection.manual.";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StockMasterCollectionConfiguration.class);
    @TempDir
    Path directory;

    @Test
    void defaultAndDisabledConfigurationsDoNotCreateCollectorOrHttpClient() {
        runner.run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(StockMasterCollectionRunner.class)
                .doesNotHaveBean(StockMasterCollectionService.class)
                .doesNotHaveBean(KisStockMasterClient.class).doesNotHaveBean(HttpClient.class));
        runner.withPropertyValues(PREFIX + "enabled=false").run(context -> assertThat(context).hasNotFailed()
                .doesNotHaveBean(StockMasterCollectionRunner.class).doesNotHaveBean(HttpClient.class));
    }

    @Test
    void explicitlyEnabledConfigurationBindsLimitsButDoesNotCollectAtBeanCreation() {
        withSettings(settings()).withPropertyValues("broker.kis.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(StockMasterCollectionRunner.class)
                    .hasSingleBean(StockMasterCollectionService.class).hasSingleBean(KisStockMasterClient.class)
                    .doesNotHaveBean(DataSource.class).doesNotHaveBean(KisTokenClient.class)
                    .doesNotHaveBean(TaskScheduler.class);
            var properties = context.getBean(StockMasterCollectionProperties.class);
            assertThat(properties.outputDirectory()).isEqualTo(directory.resolve("observations").toString());
            assertThat(properties.connectTimeout().toMillis()).isEqualTo(1000);
            assertThat(properties.downloadTimeout().toMillis()).isEqualTo(5000);
            assertThat(properties.maxArchiveBytes()).isEqualTo(1024);
            assertThat(properties.maxExtractedBytes()).isEqualTo(2048L);
            var httpClient = context.getBean(HttpClient.class);
            assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(httpClient.authenticator()).isEmpty();
            assertThat(directory.resolve("observations")).doesNotExist();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
            "output-directory;outputDirectory must not be blank.",
            "connect-timeout;connectTimeout must not be null.",
            "download-timeout;downloadTimeout must not be null.",
            "max-archive-bytes;maxArchiveBytes must not be null.",
            "max-extracted-bytes;maxExtractedBytes must not be null."
    }, delimiter = ';')
    void rejectsMissingLimitsWithoutCreatingOutputDirectory(String missing, String message) {
        var values = settings();
        values.remove(missing);
        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(message);
            assertThat(directory.resolve("observations")).doesNotExist();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"connect-timeout=0s", "download-timeout=0s", "connect-timeout=6s",
            "download-timeout=6m", "max-archive-bytes=0", "max-archive-bytes=67108865",
            "max-extracted-bytes=0", "max-extracted-bytes=268435457"})
    void rejectsInvalidLimits(String setting) {
        var values = settings();
        String[] parts = setting.split("=", 2);
        values.put(parts[0], parts[1]);
        withSettings(values).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void actualIsolatedStartupDoesNotScanMainApplicationOrBindBrokerCredentials() {
        try (var context = new SpringApplicationBuilder(StockMasterCollectionConfiguration.class)
                .web(WebApplicationType.NONE).run("--" + PREFIX + "enabled=false", "--broker.kis.enabled=true")) {
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(KisTokenClient.class)).isEmpty();
            assertThat(context.getBeansOfType(TaskScheduler.class)).isEmpty();
            assertThat(context.getBeansOfType(HttpClient.class)).isEmpty();
            assertThat(context.getBeansOfType(StockMasterCollectionRunner.class)).isEmpty();
        }
    }

    private Map<String, String> settings() {
        var values = new LinkedHashMap<String, String>();
        values.put("enabled", "true");
        values.put("output-directory", directory.resolve("observations").toString());
        values.put("connect-timeout", "1s");
        values.put("download-timeout", "5s");
        values.put("max-archive-bytes", "1024");
        values.put("max-extracted-bytes", "2048");
        return values;
    }

    private ApplicationContextRunner withSettings(Map<String, String> values) {
        return runner.withPropertyValues(values.entrySet().stream()
                .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }
}
