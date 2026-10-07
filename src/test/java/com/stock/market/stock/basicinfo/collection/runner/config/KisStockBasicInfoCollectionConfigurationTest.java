package com.stock.market.stock.basicinfo.collection.runner.config;

import com.stock.StockAgentHarnessApplication;
import com.stock.agent.InvestmentAgent;
import com.stock.agent.provider.ai.openai.client.OpenAiResponsesClient;
import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.order.KisCashOrderClient;
import com.stock.broker.order.cancellation.provider.BrokerOrderCancellationProvider;
import com.stock.broker.order.provider.BrokerOrderProvider;
import com.stock.harness.InvestmentHarness;
import com.stock.harness.scheduler.HarnessScheduler;
import com.stock.market.stock.basicinfo.collection.KisStockBasicInfoCollectionService;
import com.stock.market.stock.basicinfo.collection.runner.KisStockBasicInfoCollectionRunner;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.config.support.KisStockBasicInfoMockHttp;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class KisStockBasicInfoCollectionConfigurationTest {
    private static final String PREFIX = "market.stock.basic-info.collection.manual.";
    private static final String KIS_PREFIX = "market.stock.basic-info.kis.";
    private static final String APP_KEY = "synthetic-readonly-key";
    private static final String APP_SECRET = "synthetic-readonly-secret";
    private static final String ACCESS_TOKEN = "synthetic-readonly-token";
    private static final String REST_CLIENT = "kisStockBasicInfoRestClient";
    private static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
    private static final String SAVED_MESSAGE = "Stock basic info raw observation saved.";
    private final KisStockBasicInfoMockHttp http = new KisStockBasicInfoMockHttp();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KisStockBasicInfoCollectionConfiguration.class)
            .withInitializer(context -> context.getBeanFactory().addBeanPostProcessor(http));

    @Test
    void absentAndDisabledSettingsDoNotRegisterDbHttpOrRunnerBeans() {
        runner.run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", KIS_PREFIX + "enabled=true", "broker.kis.enabled=true")
                .run(this::assertDisabled);
        http.verify();
    }

    @Test
    void manualConfigurationAndRunnerAreNotCandidatesForNormalComponentScanning() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        scanner.setEnvironment(new MockEnvironment().withProperty(PREFIX + "enabled", "true"));

        assertThat(scanner.findCandidateComponents("com.stock.market.stock.basicinfo.collection.runner"))
                .extracting(value -> value.getBeanClassName())
                .doesNotContain(KisStockBasicInfoCollectionConfiguration.class.getName(), KisStockBasicInfoCollectionRunner.class.getName());
    }

    @Test
    void enabledManualRunRequiresDedicatedKisSettingsBeforeRequests() {
        withSettings(settings(newDatabaseUrl())).withPropertyValues(KIS_PREFIX + "enabled=false").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("Manual collection requires both manual and KIS basic info settings to be enabled.");
        });
        http.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"app-key", "app-secret"})
    void missingCredentialFailsWithoutIssuingTokensOrQueries(String missing) {
        var values = settings(newDatabaseUrl());
        values.put(KIS_PREFIX + missing, "");
        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(context.getStartupFailure()).hasStackTraceContaining("must be nonblank visible ASCII without whitespace.")
                    .hasStackTraceContaining("KisStockBasicInfoProperties");
        });
        http.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "005930,0004Y0"})
    void invalidSymbolFailsBeforeRequests(String symbol) {
        withSettings(settings(newDatabaseUrl())).withPropertyValues(PREFIX + "symbol=" + symbol).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
        });
        http.verify();
    }

    @Test
    void missingDatabaseUrlDoesNotFallBackToAnEmbeddedDatabase() {
        withSettings(settings("")).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("spring.datasource.url must be explicitly configured for manual collection.");
        });
        http.verify();
    }

    @Test
    void readySchemaRegistersOnlyObservationPersistenceAndDedicatedCollectorWithoutCollecting(CapturedOutput output) throws SQLException {
        try (var connection = preparedDatabase()) {
            withSettings(settings(connection.getMetaData().getURL())).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(DataSource.class).hasSingleBean(EntityManagerFactory.class)
                        .hasSingleBean(PlatformTransactionManager.class).hasSingleBean(KisStockBasicInfoObservationRepository.class)
                        .hasSingleBean(KisStockBasicInfoObservationStore.class).hasSingleBean(KisStockBasicInfoClient.class)
                        .hasSingleBean(KisStockBasicInfoProvider.class).hasSingleBean(KisStockBasicInfoCollectionService.class)
                        .hasSingleBean(KisStockBasicInfoCollectionRunner.class).hasSingleBean(ApplicationRunner.class)
                        .hasSingleBean(KisTokenClient.class).hasSingleBean(KisTokenProvider.class);
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
                assertNoUnrelatedBeans(context);
                assertThat(context.getBean(KisStockBasicInfoObservationRepository.class).count()).isZero();
                assertThat(context.getBean(EntityManagerFactory.class).getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
                http.verify();
            });
            assertSentinelPreserved(connection);
        }
        assertThat(output).doesNotContain(SAVED_MESSAGE, APP_KEY, APP_SECRET, ACCESS_TOKEN);
    }

    @Test
    void schemaCustomizerForcesGlobalAndOrmValidationAndDisablesJpaScripts() throws SQLException {
        try (var connection = preparedDatabase()) {
            withSettings(settings(connection.getMetaData().getURL())).run(context -> {
                assertThat(context).hasNotFailed();
                Map<String, Object> properties = new LinkedHashMap<>();
                for (String suffix : List.of("", ".orm")) {
                    properties.put("hibernate.hbm2ddl.auto" + suffix, "create-drop");
                    for (String prefix : List.of("jakarta.persistence", "javax.persistence")) {
                        properties.put(prefix + ".schema-generation.database.action" + suffix, "drop-and-create");
                        properties.put(prefix + ".schema-generation.scripts.action" + suffix, "drop-and-create");
                    }
                }
                context.getBean(KisStockBasicInfoCollectionConfiguration.class).kisStockBasicInfoCollectionSchemaValidation().customize(properties);
                assertThat(properties).hasSize(10);
                for (String suffix : List.of("", ".orm")) {
                    assertThat(properties).containsEntry("hibernate.hbm2ddl.auto" + suffix, "validate");
                    for (String prefix : List.of("jakarta.persistence", "javax.persistence")) {
                        assertThat(properties).containsEntry(prefix + ".schema-generation.database.action" + suffix, "validate")
                                .containsEntry(prefix + ".schema-generation.scripts.action" + suffix, "none");
                    }
                }
                http.verify();
            });
            assertSentinelPreserved(connection);
        }
    }

    @Test
    void actualDisabledStartupIgnoresEnabledBrokerAgentAndSchedulerFlagsWithoutDbOrHttp(CapturedOutput output) {
        try (var context = new SpringApplicationBuilder(KisStockBasicInfoCollectionConfiguration.class)
                .web(WebApplicationType.NONE).run("--" + PREFIX + "enabled=false", "--" + KIS_PREFIX + "enabled=true",
                        "--broker.kis.enabled=true", "--harness.scheduler.enabled=true", "--agent.provider.ai.openai.enabled=true")) {
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(HttpClient.class)).isEmpty();
            assertThat(context.getBeansOfType(ApplicationRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(InvestmentHarness.class)).isEmpty();
            assertThat(context.getBeansOfType(BrokerOrderProvider.class)).isEmpty();
            assertThat(context.getBeansOfType(OpenAiResponsesClient.class)).isEmpty();
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
            assertThat(context.getBeansOfType(WebServerFactory.class)).isEmpty();
        }
        assertThat(output).doesNotContain(SAVED_MESSAGE);
    }

    @Test
    void actualIsolatedStartupCollectsOneRawObservationThenClosesItsDbPool(CapturedOutput output) throws SQLException {
        byte[] bytes = KisStockBasicInfoObservationFixture.content();
        HikariDataSource pool;
        try (var connection = preparedDatabase()) {
            var builder = new SpringApplicationBuilder(KisStockBasicInfoCollectionConfiguration.class).web(WebApplicationType.NONE)
                    .initializers(context -> {
                        context.getBeanFactory().addBeanPostProcessor(http);
                        context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                            @Override
                            public Object postProcessAfterInitialization(Object bean, String beanName) {
                                if (beanName.equals(REST_CLIENT)) {
                                    expectSingleCollection(bytes);
                                }
                                return bean;
                            }
                        });
                    });
            try (var context = builder.run(arguments(settings(connection.getMetaData().getURL())))) {
                assertThat(context.getBeansOfType(ApplicationRunner.class)).hasSize(1);
                assertThat(context.getBeansOfType(InvestmentHarness.class)).isEmpty();
                assertThat(context.getBeansOfType(HarnessScheduler.class)).isEmpty();
                assertThat(context.getBeansOfType(BrokerOrderProvider.class)).isEmpty();
                assertThat(context.getBeansOfType(OpenAiResponsesClient.class)).isEmpty();
                assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
                assertThat(context.getBeansOfType(WebServerFactory.class)).isEmpty();
                assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
                assertThat(context.getBeansOfType(DataSourceScriptDatabaseInitializer.class)).isEmpty();
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
                var row = context.getBean(KisStockBasicInfoObservationRepository.class).findAll().getFirst();
                assertThat(context.getBean(KisStockBasicInfoObservationRepository.class).count()).isEqualTo(1L);
                assertThat(row.getId()).isPositive();
                assertThat(row.getContentSha256()).isEqualTo(sha256(bytes));
                var restored = context.getBean(KisStockBasicInfoObservationStore.class).findById(row.getId()).orElseThrow();
                assertThat(restored.requestedSymbol()).isEqualTo(SYMBOL);
                assertThat(restored.content()).isEqualTo(bytes);
                assertThat(restored.requestStartedAt().getNano() % 1000).isZero();
                assertThat(restored.responseReceivedAt()).isAfterOrEqualTo(restored.requestStartedAt());
                assertThat(output).contains(SAVED_MESSAGE, "symbol=" + SYMBOL, "observationId=" + row.getId())
                        .doesNotContain(APP_KEY, APP_SECRET, ACCESS_TOKEN, "original-payload", "AS_OF_VERIFIED");
                pool = context.getBean(HikariDataSource.class);
                http.verify();
            }
            assertThat(pool.isClosed()).isTrue();
            assertSentinelPreserved(connection);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM kis_stock_basic_info_observation")).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "incomplete"})
    void missingOrIncompleteSchemaFailsBeforeApiCallsWithoutCreatingUpdatingOrDroppingTables(String schema, CapturedOutput output) throws SQLException {
        try (var connection = DriverManager.getConnection(newDatabaseUrl(), "sa", "")) {
            if (schema.equals("incomplete")) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource("""
                        CREATE TABLE kis_stock_basic_info_observation (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY);
                        INSERT INTO kis_stock_basic_info_observation (id) VALUES (41);
                        """.getBytes(StandardCharsets.UTF_8)));
            }
            assertThatThrownBy(() -> new SpringApplicationBuilder(KisStockBasicInfoCollectionConfiguration.class)
                    .web(WebApplicationType.NONE)
                    .initializers(context -> context.getBeanFactory().addBeanPostProcessor(http))
                    .run(arguments(settings(connection.getMetaData().getURL()))))
                    .hasStackTraceContaining("Schema-validation: missing " + (schema.equals("missing") ? "table" : "column"));
            http.verify();
            if (schema.equals("incomplete")) {
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM kis_stock_basic_info_observation WHERE id = 41")).isEqualTo(1);
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'KIS_STOCK_BASIC_INFO_OBSERVATION'"))
                        .isEqualTo(1);
            } else {
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")).isZero();
            }
        }
        assertThat(output).doesNotContain(SAVED_MESSAGE, APP_KEY, APP_SECRET, ACCESS_TOKEN);
    }

    private void assertDisabled(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class).doesNotHaveBean(EntityManagerFactory.class)
                .doesNotHaveBean(HttpClient.class).doesNotHaveBean(KisTokenClient.class).doesNotHaveBean(KisTokenProvider.class)
                .doesNotHaveBean(KisStockBasicInfoCollectionRunner.class).doesNotHaveBean(KisStockBasicInfoCollectionService.class)
                .doesNotHaveBean(KisStockBasicInfoObservationStore.class).doesNotHaveBean(KisStockBasicInfoObservationRepository.class);
        assertNoUnrelatedBeans(context);
    }

    private void assertNoUnrelatedBeans(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean(StockAgentHarnessApplication.class).doesNotHaveBean(InvestmentHarness.class)
                .doesNotHaveBean(InvestmentAgent.class).doesNotHaveBean(HarnessScheduler.class).doesNotHaveBean(KisCashOrderClient.class)
                .doesNotHaveBean(BrokerOrderProvider.class).doesNotHaveBean(BrokerOrderCancellationProvider.class)
                .doesNotHaveBean(OpenAiResponsesClient.class).doesNotHaveBean(TaskScheduler.class)
                .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class).doesNotHaveBean(WebServerFactory.class)
                .doesNotHaveBean(Flyway.class).doesNotHaveBean(DataSourceScriptDatabaseInitializer.class);
    }

    private void assertOnlyObservationEntity(EntityManagerFactory factory) {
        assertThat(factory.getMetamodel().getEntities()).singleElement()
                .satisfies(entity -> assertThat(entity.getJavaType()).isEqualTo(KisStockBasicInfoObservationEntity.class));
    }

    private Map<String, String> settings(String url) {
        var values = new LinkedHashMap<String, String>();
        values.put(PREFIX + "enabled", "true");
        values.put(PREFIX + "symbol", SYMBOL);
        values.put(KIS_PREFIX + "enabled", "true");
        values.put(KIS_PREFIX + "app-key", APP_KEY);
        values.put(KIS_PREFIX + "app-secret", APP_SECRET);
        values.put("spring.datasource.url", url);
        values.put("spring.datasource.username", "sa");
        values.put("spring.datasource.password", "");
        values.put("spring.datasource.driver-class-name", "org.h2.Driver");
        values.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        values.put("spring.jpa.generate-ddl", "true");
        values.put("spring.jpa.properties.hibernate.hbm2ddl.auto", "update");
        values.put("spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "drop-and-create");
        values.put("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", "drop-and-create");
        values.put("spring.jpa.properties.jakarta.persistence.schema-generation.database.action.orm", "drop-and-create");
        values.put("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action.orm", "drop-and-create");
        values.put("spring.flyway.enabled", "true");
        values.put("spring.sql.init.mode", "always");
        values.put("spring.sql.init.schema-locations", "classpath:db/migration/V7__create_kis_stock_basic_info_observation.sql");
        values.put("broker.kis.enabled", "true");
        values.put("harness.scheduler.enabled", "true");
        values.put("agent.provider.ai.openai.enabled", "true");
        return values;
    }

    private ApplicationContextRunner withSettings(Map<String, String> values) {
        return runner.withPropertyValues(values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    private String[] arguments(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> "--" + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
    }

    private String newDatabaseUrl() {
        return "jdbc:h2:mem:kis_basic_info_manual_" + UUID.randomUUID();
    }

    private Connection preparedDatabase() throws SQLException {
        var connection = DriverManager.getConnection(newDatabaseUrl(), "sa", "");
        try {
            ScriptUtils.executeSqlScript(connection, new ByteArrayResource("""
                    CREATE TABLE kis_stock_basic_info_observation (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      requested_symbol VARCHAR(6) NOT NULL,
                      http_status INTEGER NOT NULL,
                      request_started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                      response_received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                      recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                      content_length INTEGER NOT NULL,
                      content_sha256 VARCHAR(64) NOT NULL,
                      raw_content BLOB NOT NULL
                    );
                    CREATE TABLE collection_sentinel (id INTEGER PRIMARY KEY);
                    INSERT INTO collection_sentinel VALUES (41);
                    """.getBytes(StandardCharsets.UTF_8)));
            return connection;
        } catch (RuntimeException failure) {
            connection.close();
            throw failure;
        }
    }

    private void assertSentinelPreserved(Connection connection) throws SQLException {
        assertThat(queryInt(connection, "SELECT COUNT(*) FROM collection_sentinel WHERE id = 41")).isEqualTo(1);
        assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'"))
                .isEqualTo(2);
    }

    private int queryInt(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private void expectSingleCollection(byte[] bytes) {
        var server = http.server(REST_CLIENT);
        server.expect(requestTo(BASE_URL + "/oauth2/tokenP")).andExpect(method(POST))
                .andExpect(request -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse())
                .andRespond(withSuccess("""
                        {"access_token":"synthetic-readonly-token","token_type":"Bearer",
                         "expires_in":3600,"access_token_token_expired":"2099-10-07 11:00:00"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/uapi/domestic-stock/v1/quotations/search-stock-info?PRDT_TYPE_CD=300&PDNO=" + SYMBOL))
                .andExpect(method(GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
                .andExpect(header("appkey", APP_KEY)).andExpect(header("appsecret", APP_SECRET))
                .andExpect(request -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse())
                .andRespond(withSuccess(bytes, MediaType.APPLICATION_JSON));
    }
}
