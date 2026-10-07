package com.stock.market.stock.basicinfo.observation.analysis.runner.config;

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
import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.query.KisStockRestrictionPrecheckQueryService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.runner.KisStockBasicInfoAnalysisRunner;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationEntity;
import com.stock.market.stock.basicinfo.observation.persistence.KisStockBasicInfoObservationRepository;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoClient;
import com.stock.market.stock.basicinfo.provider.kis.KisStockBasicInfoProvider;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.fields;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.RECORDED_AT;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.normalized;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class KisStockBasicInfoAnalysisConfigurationTest {
    private static final String PREFIX = "market.stock.basic-info.analysis.manual.";
    private static final String COMPLETED_MESSAGE = "Stored stock basic info analysis complete.";
    private static final String RESTRICTION_COMPLETED_MESSAGE = "Stored stock restriction analysis complete.";
    private static final String FRESHNESS_COMPLETED_MESSAGE = "Stored stock restriction freshness check complete.";
    private static final String PRECHECK_COMPLETED_MESSAGE = "Stored stock restriction precheck complete.";
    private static final UUID UNUSED_COLLECTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KisStockBasicInfoAnalysisConfiguration.class);
    @TempDir
    Path root;

    @Test
    void absentOrDisabledSettingsDoNotRegisterDatabaseParserOrAnalysisBeans() {
        runner.run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", "broker.kis.enabled=true", "market.stock.basic-info.kis.enabled=true",
                "harness.scheduler.enabled=true", "agent.provider.ai.openai.enabled=true").run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", PREFIX + "include-market-warnings=true").run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", PREFIX + "include-market-warnings=true", PREFIX + "check-freshness=true")
                .run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", PREFIX + "run-precheck=true").run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", PREFIX + "include-market-warnings=true", PREFIX + "check-freshness=true",
                PREFIX + "run-precheck=true").run(this::assertDisabled);
        runner.withPropertyValues(PREFIX + "enabled=false", PREFIX + "symbol=0004Y0", PREFIX + "run-precheck=true").run(this::assertDisabled);
    }

    @Test
    void manualConfigurationAndRunnerAreNotDiscoveredByNormalComponentScanning() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        scanner.setEnvironment(new MockEnvironment().withProperty(PREFIX + "enabled", "true")
                .withProperty(PREFIX + "include-market-warnings", "true").withProperty(PREFIX + "check-freshness", "true")
                .withProperty(PREFIX + "run-precheck", "true").withProperty(PREFIX + "symbol", "0004Y0"));

        assertThat(scanner.findCandidateComponents("com.stock.market.stock.basicinfo.observation.analysis"))
                .extracting(value -> value.getBeanClassName()).doesNotContain(KisStockBasicInfoAnalysisConfiguration.class.getName(),
                        KisStockBasicInfoAnalysisRunner.class.getName(), KisStockRestrictionPrecheckService.class.getName(),
                        KisStockRestrictionPrecheckQueryService.class.getName());
        assertThat(scanner.findCandidateComponents("com.stock.strategy.universe.eligibility.restriction.kis.freshness")).isEmpty();
        assertThat(scanner.findCandidateComponents("com.stock.strategy.universe.eligibility.restriction.kis.precheck")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void explicitDatabaseUrlIsRequiredInsteadOfFallingBackToAnEmbeddedDatabase(String url) {
        withSettings(settings(url, UNUSED_COLLECTION)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("spring.datasource.url must be explicitly configured for manual analysis.");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "17,18"})
    void invalidObservationSettingFailsStartupWithoutRunningAnalysis(String id, CapturedOutput output) {
        var settings = settings(newDatabaseUrl(), UNUSED_COLLECTION);
        settings.put(PREFIX + "observation-id", id);
        withSettings(settings).run(context -> assertThat(context).hasFailed());
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void readyContextRegistersOnlyObservationPersistenceAndOfflineAnalysisWithoutRunningIt(CapturedOutput output) throws SQLException {
        try (var connection = preparedDatabase(response().content())) {
            withSettings(settings(connection.getMetaData().getURL(), UNUSED_COLLECTION)).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(DataSource.class).hasSingleBean(EntityManagerFactory.class)
                        .hasSingleBean(PlatformTransactionManager.class).hasSingleBean(KisStockBasicInfoObservationRepository.class)
                        .hasSingleBean(KisStockBasicInfoObservationStore.class).hasSingleBean(StockMasterBatchParsingService.class)
                        .hasSingleBean(KisStockBasicInfoAnalysisService.class).hasSingleBean(KisStockBasicInfoAnalysisRunner.class)
                        .hasSingleBean(ApplicationRunner.class);
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
                assertNoUnrelatedBeans(context);
                assertThat(context.getBeansOfType(KisStockRestrictionFreshnessPolicy.class)).isEmpty();
                assertNoMarketWarningBeans(context);
                assertThat(context.getBean(EntityManagerFactory.class).getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
                assertThat(context.getBean(KisStockBasicInfoObservationRepository.class).count()).isEqualTo(1L);
            });
            assertDatabasePreserved(connection, response().content());
        }
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, "synthetic-unrelated-private-key");
        assertThat(root).isEmptyDirectory();
    }

    @Test
    void schemaCustomizerPinsGlobalOrmAndLegacyDdlActionsToValidationAndDisablesScripts() throws SQLException {
        try (var connection = preparedDatabase(response().content())) {
            withSettings(settings(connection.getMetaData().getURL(), UNUSED_COLLECTION)).run(context -> {
                assertThat(context).hasNotFailed();
                Map<String, Object> properties = new LinkedHashMap<>();
                for (String suffix : List.of("", ".orm")) {
                    properties.put("hibernate.hbm2ddl.auto" + suffix, "create-drop");
                    for (String prefix : List.of("jakarta.persistence", "javax.persistence")) {
                        properties.put(prefix + ".schema-generation.database.action" + suffix, "drop-and-create");
                        properties.put(prefix + ".schema-generation.scripts.action" + suffix, "drop-and-create");
                    }
                }
                context.getBean(KisStockBasicInfoAnalysisConfiguration.class).kisStockBasicInfoAnalysisSchemaValidation().customize(properties);
                assertThat(properties).hasSize(10);
                for (String suffix : List.of("", ".orm")) {
                    assertThat(properties).containsEntry("hibernate.hbm2ddl.auto" + suffix, "validate");
                    for (String prefix : List.of("jakarta.persistence", "javax.persistence")) {
                        assertThat(properties).containsEntry(prefix + ".schema-generation.database.action" + suffix, "validate")
                                .containsEntry(prefix + ".schema-generation.scripts.action" + suffix, "none");
                    }
                }
            });
            assertDatabasePreserved(connection, response().content());
        }
    }

    @Test
    void actualDisabledStartupIgnoresOtherEnabledFlagsWithoutDbOrFileAccess(CapturedOutput output) {
        try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                .run("--" + PREFIX + "enabled=false", "--broker.kis.enabled=true", "--market.stock.basic-info.kis.enabled=true",
                        "--harness.scheduler.enabled=true", "--agent.provider.ai.openai.enabled=true")) {
            assertDisabled(context);
        }
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void disabledMainEntryPointReturnsWithoutDatabaseOrAnalysis(CapturedOutput output) {
        KisStockBasicInfoAnalysisRunner.main(new String[]{"--" + PREFIX + "enabled=false", "--broker.kis.enabled=true"});

        assertThat(output).contains("Stock basic info manual analysis process finished.").doesNotContain(COMPLETED_MESSAGE);
        assertThat(root).isEmptyDirectory();
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "Y", " "})
    void actualStartupAnalyzesPreservedMasterAndStoredObservationWithoutChangingEvidenceThenClosesPool(
            String suspension, CapturedOutput output
    ) throws Exception {
        var original = response(SYMBOL, fields().put("tr_stop_yn", suspension).put("prdt_name", "synthetic-private-name"));
        var collection = collectMaster();
        var master = new StockMasterBatchParsingService(new KisStockMasterParser()).parseBatch(root, collection.collectionId());
        var expected = new KisStockBasicInfoAnalysisResult(17L, normalized(original), screening(master, original));
        var filesBefore = snapshotFiles();
        HikariDataSource pool;
        try (var connection = preparedDatabase(original.content())) {
            try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .run(arguments(settings(connection.getMetaData().getURL(), collection.collectionId())))) {
                assertNoUnrelatedBeans(context);
                assertNoMarketWarningBeans(context);
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
                assertThat(context.getBeansOfType(ApplicationRunner.class)).hasSize(1);
                assertThat(context.getBean(KisStockBasicInfoAnalysisService.class).analyze(17L, master)).isEqualTo(expected);
                assertThat(context.getBean(KisStockBasicInfoObservationRepository.class).count()).isEqualTo(1L);
                pool = context.getBean(HikariDataSource.class);
                assertThat(output).contains(COMPLETED_MESSAGE, "observationId=17", "collectionId=" + collection.collectionId(),
                                "symbol=" + SYMBOL, "restrictionStatus=" + expected.screeningResult().status(),
                                "typeReason=" + expected.screeningResult().observation().typeResolution().reasonCode())
                        .doesNotContain("synthetic-private-name", "synthetic-unrelated-private-key", "pdno", "msg1", "AS_OF_VERIFIED");
            }
            assertThat(pool.isClosed()).isTrue();
            assertDatabasePreserved(connection, original.content());
        }
        assertFilesUnchanged(filesBefore);
        assertThat(root.resolve("forbidden-schema.sql")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-observation", "business-failure", "corrupt-master"})
    void actualAnalysisFailureClosesPoolWithoutChangingDbOrMasterEvidence(String failure, CapturedOutput output) throws Exception {
        var collection = collectMaster();
        byte[] bytes = failure.equals("business-failure")
                ? "{\"rt_cd\":\"1\",\"msg1\":\"synthetic-private-body\"}".getBytes(StandardCharsets.UTF_8) : response().content();
        if (failure.equals("corrupt-master")) {
            Path mst = root.resolve(collection.collectionId().toString()).resolve("KOSPI/kospi_code.mst");
            byte[] changed = Files.readAllBytes(mst);
            changed[0] ^= 1;
            Files.write(mst, changed);
        }
        var filesBefore = snapshotFiles();
        var pool = new AtomicReference<HikariDataSource>();
        try (var connection = preparedDatabase(bytes)) {
            var settings = settings(connection.getMetaData().getURL(), collection.collectionId());
            if (failure.equals("missing-observation")) {
                settings.put(PREFIX + "observation-id", "18");
            }
            var builder = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .initializers(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName) {
                            if (bean instanceof HikariDataSource dataSource) {
                                pool.set(dataSource);
                            }
                            return bean;
                        }
                    }));

            String expectedMessage = switch (failure) {
                case "missing-observation" -> "KIS stock basic info observation not found. id=18";
                case "business-failure" -> "rt_cd must be the success string 0.";
                case "corrupt-master" -> "File bytes or SHA-256 do not match the manifest.";
                default -> throw new IllegalArgumentException("Unexpected test failure.");
            };
            assertThatThrownBy(() -> builder.run(arguments(settings))).hasStackTraceContaining(expectedMessage);

            assertThat(pool.get()).isNotNull();
            assertThat(pool.get().isClosed()).isTrue();
            assertDatabasePreserved(connection, bytes);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, "synthetic-private-body", "synthetic-unrelated-private-key");
    }

    @ParameterizedTest
    @CsvSource({"missing,false", "incomplete,false", "missing,true", "incomplete,true"})
    void missingOrIncompleteSchemaFailsWithoutCreatingUpdatingOrDroppingTables(String schema, boolean includeWarnings, CapturedOutput output)
            throws SQLException {
        var pool = new AtomicReference<HikariDataSource>();
        try (var connection = DriverManager.getConnection(newDatabaseUrl(), "sa", "")) {
            if (schema.equals("incomplete")) {
                ScriptUtils.executeSqlScript(connection, new ByteArrayResource("""
                        CREATE TABLE kis_stock_basic_info_observation (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY);
                        INSERT INTO kis_stock_basic_info_observation VALUES (17);
                        """.getBytes(StandardCharsets.UTF_8)));
            }
            var builder = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .initializers(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName) {
                            if (bean instanceof HikariDataSource dataSource) {
                                pool.set(dataSource);
                            }
                            return bean;
                        }
                    }));
            var settings = settings(connection.getMetaData().getURL(), UNUSED_COLLECTION);
            if (includeWarnings) {
                settings.put(PREFIX + "include-market-warnings", "true");
                settings.put(PREFIX + "warning-market", "KOSDAQ");
            }
            assertThatThrownBy(() -> builder.run(arguments(settings)))
                    .hasStackTraceContaining("Schema-validation: missing " + (schema.equals("missing") ? "table" : "column"));
            assertThat(pool.get()).isNotNull();
            assertThat(pool.get().isClosed()).isTrue();
            if (schema.equals("incomplete")) {
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM kis_stock_basic_info_observation WHERE id = 17")).isEqualTo(1);
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = 'KIS_STOCK_BASIC_INFO_OBSERVATION'"))
                        .isEqualTo(1);
            } else {
                assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")).isZero();
            }
        }
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @Test
    void combinedContextRegistersOnlyRequiredWarningBeansWithoutRunningAnalysis(CapturedOutput output) throws SQLException {
        try (var connection = preparedDatabase(response().content())) {
            var settings = settings(connection.getMetaData().getURL(), UNUSED_COLLECTION);
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", "KOSDAQ");
            withSettings(settings).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(KisStockMasterMarketWarningParser.class)
                        .hasSingleBean(KisStockMarketWarningObservationPolicy.class).hasSingleBean(KisStockRestrictionAnalysisService.class)
                        .hasSingleBean(KisStockBasicInfoAnalysisService.class).hasSingleBean(ApplicationRunner.class);
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
                assertNoUnrelatedBeans(context);
            });
            assertDatabasePreserved(connection, response().content());
        }
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, "synthetic-unrelated-private-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "", "UNSUPPORTED", "KOSPI,KOSDAQ"})
    void invalidCombinedMarketStopsStartupWithoutAnalysis(String market, CapturedOutput output) {
        var settings = settings(newDatabaseUrl(), UNUSED_COLLECTION);
        settings.put(PREFIX + "include-market-warnings", "true");
        if (!market.equals("missing")) {
            settings.put(PREFIX + "warning-market", market);
        }

        withSettings(settings).run(context -> assertThat(context).hasFailed());

        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("combinedCases")
    void combinedStartupMatchesDirectPoliciesLoadsOneObservationPreservesEvidenceAndClosesPool(
            KisStockMasterMarket market, String code, String preannouncement, CapturedOutput output
    ) throws Exception {
        var original = KisStockRestrictionAnalysisFixture.inputs(market, code, preannouncement, Map.of(), Map.of()).response();
        var collection = collectMaster(market, code, preannouncement);
        var master = new StockMasterBatchParsingService(new KisStockMasterParser()).parseBatch(root, collection.collectionId());
        var warnings = KisStockRestrictionScreeningFixture.warnings(master, market);
        var expectedBasic = screening(master, original);
        var expected = new KisStockRestrictionScreeningPolicy().evaluate(expectedBasic, warnings);
        var filesBefore = snapshotFiles();
        HikariDataSource pool;
        try (var connection = preparedDatabase(original)) {
            var settings = settings(connection.getMetaData().getURL(), collection.collectionId());
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", market.name());
            settings.put("spring.jpa.properties.hibernate.generate_statistics", "true");
            try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .run(arguments(settings))) {
                assertNoUnrelatedBeans(context);
                var factory = context.getBean(EntityManagerFactory.class);
                assertOnlyObservationEntity(factory);
                assertThat(factory.unwrap(SessionFactory.class).getStatistics().getEntityLoadCount()).isEqualTo(1L);
                assertThat(context.getBeansOfType(ApplicationRunner.class)).hasSize(1);
                assertThat(context.getBeansOfType(KisStockRestrictionAnalysisService.class)).hasSize(1);
                assertThat(context.getBeansOfType(KisStockRestrictionFreshnessPolicy.class)).isEmpty();
                pool = context.getBean(HikariDataSource.class);
                assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, "observationId=17",
                                "collectionId=" + collection.collectionId(), "symbol=" + original.requestedSymbol(), "warningMarket=" + market,
                                "warningInputSha256=" + warnings.source().source().inputSha256(),
                                "restrictionStatus=" + expectedBasic.status(), "combinedRestrictionStatus=" + expected.status(),
                                "combinedRestrictionReasons=" + expected.reasonCodes())
                        .doesNotContain(FRESHNESS_COMPLETED_MESSAGE, "synthetic-unrelated-private-key", "pdno", "msg1", "AS_OF_VERIFIED", "rawLine=", "masterBatch=");
            }
            assertThat(pool.isClosed()).isTrue();
            assertDatabasePreserved(connection, original);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(root.resolve("forbidden-schema.sql")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitWrongMarketRemainsReviewInActualStartupInsteadOfSelectingTheOtherMarket(boolean runPrecheck, CapturedOutput output) throws Exception {
        var collection = collectMaster(KOSDAQ, "03", "Y");
        var filesBefore = snapshotFiles();
        try (var connection = preparedDatabase(response().content())) {
            var settings = settings(connection.getMetaData().getURL(), collection.collectionId());
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", "KOSPI");
            addFreshnessSettings(settings, new KisStockRestrictionFreshnessRequest(
                    normalized(response()).responseReceivedAt().plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1)));
            settings.put(PREFIX + "run-precheck", Boolean.toString(runPrecheck));
            HikariDataSource pool;
            try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .run(arguments(settings))) {
                assertNoUnrelatedBeans(context);
                pool = context.getBean(HikariDataSource.class);
                assertThat(output).contains(RESTRICTION_COMPLETED_MESSAGE, "warningMarket=KOSPI", "combinedRestrictionStatus=REVIEW_REQUIRED",
                                "combinedRestrictionReasons=[MARKET_WARNING_MARKET_NOT_MATCHED]", FRESHNESS_COMPLETED_MESSAGE,
                                "freshnessStatus=TIME_UNVERIFIED", "freshnessReasons=[MASTER_OBSERVATION_SOURCE_UNVERIFIED]")
                        .doesNotContain("MARKET_WARNING_INVESTMENT_RISK_OBSERVED", "MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED");
                if (runPrecheck) {
                    assertThat(output).contains(PRECHECK_COMPLETED_MESSAGE, "precheckStatus=BLOCKED");
                } else {
                    assertThat(output).doesNotContain(PRECHECK_COMPLETED_MESSAGE);
                }
            }
            assertThat(pool.isClosed()).isTrue();
            assertDatabasePreserved(connection, response().content());
        }
        assertFilesUnchanged(filesBefore);
    }

    @ParameterizedTest
    @CsvSource({"missing-observation,false", "business-failure,false", "corrupt-master,false",
            "missing-observation,true", "business-failure,true", "corrupt-master,true"})
    void combinedStartupFailureClosesPoolWithoutRepairingEvidenceOrLoggingCompletion(String failure, boolean runPrecheck, CapturedOutput output)
            throws Exception {
        var collection = collectMaster(KOSDAQ, "02", "N");
        byte[] bytes = failure.equals("business-failure")
                ? "{\"rt_cd\":\"1\",\"msg1\":\"synthetic-private-body\"}".getBytes(StandardCharsets.UTF_8) : response().content();
        if (failure.equals("corrupt-master")) {
            Path mst = root.resolve(collection.collectionId().toString()).resolve("KOSDAQ/kosdaq_code.mst");
            byte[] changed = Files.readAllBytes(mst);
            changed[0] ^= 1;
            Files.write(mst, changed);
        }
        var filesBefore = snapshotFiles();
        var pool = new AtomicReference<HikariDataSource>();
        try (var connection = preparedDatabase(bytes)) {
            var settings = settings(connection.getMetaData().getURL(), collection.collectionId());
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", "KOSDAQ");
            if (runPrecheck) {
                settings.put(PREFIX + "run-precheck", "true");
                addFreshnessSettings(settings, new KisStockRestrictionFreshnessRequest(
                        normalized(response()).responseReceivedAt().plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1)));
            }
            if (failure.equals("missing-observation")) {
                settings.put(PREFIX + "observation-id", "18");
            }
            var builder = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .initializers(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName) {
                            if (bean instanceof HikariDataSource dataSource) {
                                pool.set(dataSource);
                            }
                            return bean;
                        }
                    }));
            String expectedMessage = switch (failure) {
                case "missing-observation" -> "KIS stock basic info observation not found. id=18";
                case "business-failure" -> "rt_cd must be the success string 0.";
                default -> "File bytes or SHA-256 do not match the manifest.";
            };

            assertThatThrownBy(() -> builder.run(arguments(settings))).hasStackTraceContaining(expectedMessage);

            assertThat(pool.get()).isNotNull();
            assertThat(pool.get().isClosed()).isTrue();
            assertDatabasePreserved(connection, bytes);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE,
                "synthetic-private-body", "synthetic-unrelated-private-key");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void combinedContextRegistersFreshnessPolicyOnlyWhenExplicitlyEnabled(boolean checkFreshness, CapturedOutput output) throws SQLException {
        try (var connection = preparedDatabase(response())) {
            var settings = settings(connection.getMetaData().getURL(), UNUSED_COLLECTION);
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", "KOSDAQ");
            if (checkFreshness) {
                addFreshnessSettings(settings, new KisStockRestrictionFreshnessRequest(
                        normalized(response()).responseReceivedAt().plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1)));
            }
            withSettings(settings).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(KisStockRestrictionAnalysisService.class).hasSingleBean(ApplicationRunner.class);
                assertThat(context.getBeansOfType(KisStockRestrictionFreshnessPolicy.class)).hasSize(checkFreshness ? 1 : 0);
                assertNoPrecheckBeans(context);
                assertNoUnrelatedBeans(context);
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
            });
            assertDatabasePreserved(connection, response());
        }
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"include-market-warnings=false", "evaluated-at=missing", "max-master-age=missing", "max-basic-info-age=missing",
            "max-master-age=0s", "max-basic-info-age=-1ns"})
    void invalidFreshnessSettingsStopStartupBeforeAnalysis(String setting, CapturedOutput output) {
        var settings = settings(newDatabaseUrl(), UNUSED_COLLECTION);
        settings.put(PREFIX + "include-market-warnings", "true");
        settings.put(PREFIX + "warning-market", "KOSDAQ");
        addFreshnessSettings(settings, new KisStockRestrictionFreshnessRequest(
                Instant.parse("2026-10-07T10:00:00Z"), Duration.ofDays(3), Duration.ofHours(1)));
        var parts = setting.split("=", 2);
        if (parts[1].equals("missing")) {
            settings.remove(PREFIX + parts[0]);
        } else {
            settings.put(PREFIX + parts[0], parts[1]);
        }

        withSettings(settings).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Failed to bind properties under 'market.stock.basic-info.analysis.manual'");
        });

        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("freshnessCases")
    void freshnessStartupMatchesDirectPolicyLoadsOneObservationPreservesEvidenceAndClosesPool(
            KisStockMasterMarket market, KisStockRestrictionFreshnessStatus status, CapturedOutput output
    ) throws Exception {
        var original = KisStockRestrictionAnalysisFixture.inputs(market, "02", "N", Map.of(), Map.of()).response();
        var collection = collectMaster(market, "02", "N");
        var master = new StockMasterBatchParsingService(new KisStockMasterParser()).parseBatch(root, collection.collectionId());
        var basic = new KisStockBasicInfoAnalysisResult(17L, normalized(original), screening(master, original));
        var warnings = KisStockRestrictionScreeningFixture.warnings(master, market);
        var combined = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warnings));
        Duration masterAge = Duration.ofDays(3).plusNanos(11);
        Duration apiAge = Duration.ofHours(1).plusNanos(19);
        Instant evaluation = switch (status) {
            case FRESH -> normalized(original).responseReceivedAt().plusSeconds(1);
            case EXPIRED -> collection.startedAt().plus(masterAge);
            case TIME_UNVERIFIED -> original.requestStartedAt().minusSeconds(1);
        };
        var request = new KisStockRestrictionFreshnessRequest(evaluation, masterAge, apiAge);
        var expected = new KisStockRestrictionFreshnessPolicy().evaluate(request, combined);
        assertThat(expected.status()).isEqualTo(status);
        var filesBefore = snapshotFiles();
        HikariDataSource pool;
        try (var connection = preparedDatabase(original)) {
            var settings = settings(connection.getMetaData().getURL(), collection.collectionId());
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", market.name());
            settings.put("spring.jpa.properties.hibernate.generate_statistics", "true");
            addFreshnessSettings(settings, request);
            try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .run(arguments(settings))) {
                assertNoUnrelatedBeans(context);
                var factory = context.getBean(EntityManagerFactory.class);
                assertOnlyObservationEntity(factory);
                assertThat(factory.unwrap(SessionFactory.class).getStatistics().getEntityLoadCount()).isEqualTo(1L);
                assertThat(context.getBeansOfType(ApplicationRunner.class)).hasSize(1);
                assertThat(context.getBeansOfType(KisStockRestrictionFreshnessPolicy.class)).hasSize(1);
                assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class).evaluatedAt()).isEqualTo(evaluation);
                pool = context.getBean(HikariDataSource.class);
                assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE,
                                "observationId=17", "collectionId=" + collection.collectionId(), "symbol=" + original.requestedSymbol(),
                                "evaluatedAt=" + evaluation, "maxMasterAge=" + masterAge, "maxBasicInfoAge=" + apiAge,
                                "freshnessStatus=" + expected.status(), "freshnessReasons=" + expected.reasonCodes(),
                                "freshnessVersion=" + expected.freshnessVersion(), "combinedRestrictionStatus=EXCLUSION_SIGNAL_OBSERVED")
                        .doesNotContain("synthetic-unrelated-private-key", "pdno", "msg1", "AS_OF_VERIFIED", "rawLine=", "masterBatch=");
            }
            assertThat(pool.isClosed()).isTrue();
            assertDatabasePreserved(connection, original);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(root.resolve("forbidden-schema.sql")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void precheckBeansAreRegisteredOnlyForExplicitOptInWithoutRunningAnalysis(boolean runPrecheck, CapturedOutput output) throws SQLException {
        try (var connection = preparedDatabase(response())) {
            var settings = settings(connection.getMetaData().getURL(), UNUSED_COLLECTION);
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", "KOSDAQ");
            settings.put(PREFIX + "run-precheck", Boolean.toString(runPrecheck));
            addFreshnessSettings(settings, new KisStockRestrictionFreshnessRequest(
                    normalized(response()).responseReceivedAt().plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1)));

            withSettings(settings).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ApplicationRunner.class);
                assertThat(context.getBeansOfType(KisStockRestrictionPrecheckPolicy.class)).hasSize(runPrecheck ? 1 : 0);
                assertThat(context.getBeansOfType(KisStockRestrictionPrecheckService.class)).hasSize(runPrecheck ? 1 : 0);
                assertThat(context.getBeansOfType(KisStockRestrictionPrecheckQueryService.class)).isEmpty();
                assertNoUnrelatedBeans(context);
                assertOnlyObservationEntity(context.getBean(EntityManagerFactory.class));
            });
            assertDatabasePreserved(connection, response());
        }
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"check-freshness=false", "include-market-warnings=false", "evaluated-at=missing"})
    void invalidPrecheckSettingsFailBindingBeforeAnalysis(String setting, CapturedOutput output) {
        var settings = settings(newDatabaseUrl(), UNUSED_COLLECTION);
        settings.put(PREFIX + "include-market-warnings", "true");
        settings.put(PREFIX + "warning-market", "KOSDAQ");
        settings.put(PREFIX + "run-precheck", "true");
        addFreshnessSettings(settings, new KisStockRestrictionFreshnessRequest(
                Instant.parse("2026-10-07T10:00:00Z"), Duration.ofDays(3), Duration.ofHours(1)));
        var parts = setting.split("=", 2);
        if (parts[1].equals("missing")) {
            settings.remove(PREFIX + parts[0]);
        } else {
            settings.put(PREFIX + parts[0], parts[1]);
        }
        withSettings(settings).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Failed to bind properties under 'market.stock.basic-info.analysis.manual'");
        });
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("precheckCases")
    void precheckStartupMatchesDirectPoliciesLoadsOneObservationPreservesEvidenceAndClosesPool(
            KisStockMasterMarket market, String code, KisStockRestrictionFreshnessStatus status, boolean bySymbol, CapturedOutput output
    ) throws Exception {
        var original = KisStockRestrictionAnalysisFixture.inputs(market, code, "N", Map.of(), Map.of("prdt_name", "synthetic-private-name")).response();
        var collection = collectMaster(market, code, "N");
        var master = new StockMasterBatchParsingService(new KisStockMasterParser()).parseBatch(root, collection.collectionId());
        var basic = new KisStockBasicInfoAnalysisResult(17L, normalized(original), screening(master, original));
        var warnings = KisStockRestrictionScreeningFixture.warnings(master, market);
        var combined = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warnings));
        Duration masterAge = Duration.ofDays(3).plusNanos(11);
        Duration apiAge = Duration.ofHours(1).plusNanos(19);
        Instant evaluation = switch (status) {
            case FRESH -> bySymbol ? RECORDED_AT.truncatedTo(ChronoUnit.MICROS).plusSeconds(1) : normalized(original).responseReceivedAt().plusSeconds(1);
            case EXPIRED -> collection.startedAt().plus(masterAge);
            case TIME_UNVERIFIED -> original.requestStartedAt().minusSeconds(1);
        };
        var request = new KisStockRestrictionFreshnessRequest(evaluation, masterAge, apiAge);
        var freshness = new KisStockRestrictionFreshnessPolicy().evaluate(request, combined);
        var expected = new KisStockRestrictionPrecheckPolicy().evaluate(freshness);
        assertThat(freshness.status()).isEqualTo(status);
        assertThat(expected.status()).isEqualTo(code.equals("00") && status == KisStockRestrictionFreshnessStatus.FRESH
                ? KisStockRestrictionPrecheckStatus.CLEAR : KisStockRestrictionPrecheckStatus.BLOCKED);
        var filesBefore = snapshotFiles();
        HikariDataSource pool;
        try (var connection = preparedDatabase(original)) {
            var settings = settings(connection.getMetaData().getURL(), collection.collectionId());
            settings.put(PREFIX + "include-market-warnings", "true");
            settings.put(PREFIX + "warning-market", market.name());
            settings.put(PREFIX + "run-precheck", "true");
            settings.put("spring.jpa.properties.hibernate.generate_statistics", "true");
            addFreshnessSettings(settings, request);
            if (bySymbol) {
                settings.remove(PREFIX + "observation-id");
                settings.put(PREFIX + "symbol", original.requestedSymbol());
            }
            try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .run(arguments(settings))) {
                assertNoUnrelatedBeans(context);
                var factory = context.getBean(EntityManagerFactory.class);
                assertOnlyObservationEntity(factory);
                var statistics = factory.unwrap(SessionFactory.class).getStatistics();
                assertThat(statistics.getEntityLoadCount()).isEqualTo(1L);
                assertThat(statistics.getEntityInsertCount()).isZero();
                assertThat(statistics.getEntityUpdateCount()).isZero();
                assertThat(statistics.getEntityDeleteCount()).isZero();
                assertThat(context.getBeansOfType(ApplicationRunner.class)).hasSize(1);
                assertThat(context.getBeansOfType(KisStockRestrictionPrecheckPolicy.class)).hasSize(1);
                assertThat(context.getBeansOfType(KisStockRestrictionPrecheckService.class)).hasSize(1);
                assertThat(context.getBeansOfType(KisStockRestrictionPrecheckQueryService.class)).hasSize(bySymbol ? 1 : 0);
                assertThat(context.getBean(KisStockBasicInfoAnalysisProperties.class).evaluatedAt()).isEqualTo(evaluation);
                pool = context.getBean(HikariDataSource.class);
                assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE,
                                "observationId=17", "collectionId=" + collection.collectionId(), "symbol=" + original.requestedSymbol(),
                                "evaluatedAt=" + evaluation, "maxMasterAge=" + masterAge, "maxBasicInfoAge=" + apiAge,
                                "combinedRestrictionStatus=" + combined.restrictionScreeningResult().status(),
                                "combinedRestrictionReasons=" + combined.restrictionScreeningResult().reasonCodes(),
                                "freshnessStatus=" + freshness.status(), "freshnessReasons=" + freshness.reasonCodes(),
                                "precheckStatus=" + expected.status(), "precheckVersion=" + expected.precheckVersion())
                        .doesNotContain("synthetic-private-name", "synthetic-unrelated-private-key", "pdno", "msg1", "AS_OF_VERIFIED", "rawLine=", "masterBatch=");
            }
            assertThat(pool.isClosed()).isTrue();
            assertDatabasePreserved(connection, original);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(root.resolve("forbidden-schema.sql")).doesNotExist();
    }

    private static Stream<Arguments> precheckCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.FRESH, false),
                Arguments.of(market, "02", KisStockRestrictionFreshnessStatus.FRESH, false),
                Arguments.of(market, "99", KisStockRestrictionFreshnessStatus.FRESH, false),
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.EXPIRED, false),
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED, false),
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.FRESH, true),
                Arguments.of(market, "02", KisStockRestrictionFreshnessStatus.FRESH, true),
                Arguments.of(market, "99", KisStockRestrictionFreshnessStatus.FRESH, true),
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.EXPIRED, true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"005930", "0004Y0"})
    void symbolContextRegistersQueryServiceWithoutSelectingOrPrecheckingAtConstruction(String symbol, CapturedOutput output) throws SQLException {
        try (var connection = preparedDatabase(response())) {
            var request = new KisStockRestrictionFreshnessRequest(RECORDED_AT.plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1));
            var settings = symbolSettings(connection.getMetaData().getURL(), UNUSED_COLLECTION, symbol, request);
            settings.put("spring.jpa.properties.hibernate.generate_statistics", "true");
            withSettings(settings).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ApplicationRunner.class).hasSingleBean(KisStockRestrictionPrecheckQueryService.class);
                var properties = context.getBean(KisStockBasicInfoAnalysisProperties.class);
                assertThat(properties.symbol()).isEqualTo(symbol);
                assertThat(properties.observationId()).isNull();
                var factory = context.getBean(EntityManagerFactory.class);
                assertOnlyObservationEntity(factory);
                assertNoUnrelatedBeans(context);
                assertThat(factory.unwrap(SessionFactory.class).getStatistics().getEntityLoadCount()).isZero();
                assertThat(factory.unwrap(SessionFactory.class).getStatistics().getQueryExecutionCount()).isZero();
            });
            assertDatabasePreserved(connection, response());
        }
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"symbol=bad", "symbol=missing", "observation-id=17", "run-precheck=false", "check-freshness=false",
            "include-market-warnings=false", "warning-market=missing", "evaluated-at=missing"})
    void invalidSymbolSettingsFailBindingBeforeAnyManualAnalysis(String setting, CapturedOutput output) {
        var values = symbolSettings(newDatabaseUrl(), UNUSED_COLLECTION, SYMBOL,
                new KisStockRestrictionFreshnessRequest(RECORDED_AT.plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1)));
        var parts = setting.split("=", 2);
        if (parts[1].equals("missing")) {
            values.remove(PREFIX + parts[0]);
        } else {
            values.put(PREFIX + parts[0], parts[1]);
        }
        withSettings(values).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Failed to bind properties under 'market.stock.basic-info.analysis.manual'");
        });
        assertThat(root).isEmptyDirectory();
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    @Test
    void actualSymbolStartupSelectsLatestAvailableIdInsteadOfOlderFutureOrOtherSymbolRows(CapturedOutput output) throws Exception {
        var original = response();
        var latest = new KisStockBasicInfoRawResponse(SYMBOL, original.requestStartedAt().plusMillis(100),
                original.responseReceivedAt().plusMillis(100), 200, original.content());
        var collection = collectMaster(KOSDAQ, "00", "N");
        var master = new StockMasterBatchParsingService(new KisStockMasterParser()).parseBatch(root, collection.collectionId());
        var warnings = KisStockRestrictionScreeningFixture.warnings(master, KOSDAQ);
        var basic = new KisStockBasicInfoAnalysisResult(29L, normalized(latest), screening(master, latest));
        var analysis = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warnings));
        var request = new KisStockRestrictionFreshnessRequest(RECORDED_AT.plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1));
        var expected = new KisStockRestrictionPrecheckPolicy().evaluate(new KisStockRestrictionFreshnessPolicy().evaluate(request, analysis));
        var filesBefore = snapshotFiles();
        HikariDataSource pool;
        try (var connection = preparedDatabase(original)) {
            insertObservation(connection, 29L, latest, RECORDED_AT.plusMillis(100));
            var futureTime = request.evaluatedAt().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.MICROS);
            insertObservation(connection, 30L, new KisStockBasicInfoRawResponse(SYMBOL, original.requestStartedAt().plusSeconds(1),
                    futureTime, 200, original.content()), futureTime);
            var other = KisStockRestrictionAnalysisFixture.inputs(KOSPI, "00", "N", Map.of(), Map.of()).response();
            insertObservation(connection, 40L, other, RECORDED_AT);
            var rowsBefore = snapshotObservationRows(connection);
            var settings = symbolSettings(connection.getMetaData().getURL(), collection.collectionId(), SYMBOL, request);
            settings.put("spring.jpa.properties.hibernate.generate_statistics", "true");
            try (var context = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .run(arguments(settings))) {
                assertNoUnrelatedBeans(context);
                var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
                assertThat(statistics.getEntityLoadCount()).isEqualTo(1L);
                assertThat(statistics.getEntityInsertCount()).isZero();
                assertThat(statistics.getEntityUpdateCount()).isZero();
                assertThat(statistics.getEntityDeleteCount()).isZero();
                pool = context.getBean(HikariDataSource.class);
                assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE,
                                "observationId=29", "symbol=" + SYMBOL, "evaluatedAt=" + request.evaluatedAt(),
                                "precheckStatus=" + expected.status(), "freshnessStatus=" + expected.freshnessResult().status())
                        .doesNotContain("observationId=17", "observationId=30", "observationId=40", "observationId=null", "synthetic-unrelated-private-key");
            }
            assertThat(pool.isClosed()).isTrue();
            assertThat(snapshotObservationRows(connection)).isEqualTo(rowsBefore);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM analysis_sentinel WHERE id = 41")).isEqualTo(1);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")).isEqualTo(2);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(root.resolve("forbidden-schema.sql")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "future-response", "future-recorded", "business-failure", "invalid-json", "corrupt-hash", "corrupt-master"})
    void actualSymbolStartupFailureClosesPoolWithoutFallbackRepairOrCompletion(String failure, CapturedOutput output) throws Exception {
        var original = response();
        var collection = collectMaster(KOSDAQ, "00", "N");
        if (failure.equals("corrupt-master")) {
            Path mst = root.resolve(collection.collectionId().toString()).resolve("KOSDAQ/kosdaq_code.mst");
            byte[] changed = Files.readAllBytes(mst);
            changed[0] ^= 1;
            Files.write(mst, changed);
        }
        var filesBefore = snapshotFiles();
        var pool = new AtomicReference<HikariDataSource>();
        var request = new KisStockRestrictionFreshnessRequest(RECORDED_AT.plusSeconds(1), Duration.ofDays(3), Duration.ofHours(1));
        try (var connection = preparedDatabase(original)) {
            if (failure.equals("business-failure") || failure.equals("invalid-json") || failure.equals("corrupt-hash")) {
                byte[] content = switch (failure) {
                    case "business-failure" -> "{\"rt_cd\":\"1\",\"msg1\":\"synthetic-private-body\"}".getBytes(StandardCharsets.UTF_8);
                    case "invalid-json" -> "not-json".getBytes(StandardCharsets.UTF_8);
                    default -> original.content();
                };
                insertObservation(connection, 29L, new KisStockBasicInfoRawResponse(SYMBOL, original.requestStartedAt().plusMillis(100),
                        original.responseReceivedAt().plusMillis(100), 200, content), RECORDED_AT.plusMillis(100));
                if (failure.equals("corrupt-hash")) {
                    try (var statement = connection.prepareStatement("UPDATE kis_stock_basic_info_observation SET content_sha256 = ? WHERE id = 29")) {
                        statement.setString(1, "0".repeat(64));
                        statement.executeUpdate();
                    }
                }
            } else if (failure.startsWith("future")) {
                String field = failure.equals("future-response") ? "response_received_at" : "recorded_at";
                try (var statement = connection.prepareStatement("UPDATE kis_stock_basic_info_observation SET " + field + " = ? WHERE id = 17")) {
                    statement.setObject(1, request.evaluatedAt().truncatedTo(ChronoUnit.MICROS).plus(1, ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
                    statement.executeUpdate();
                }
            }
            var rowsBefore = snapshotObservationRows(connection);
            var settings = symbolSettings(connection.getMetaData().getURL(), collection.collectionId(), failure.equals("missing") ? "000660" : SYMBOL, request);
            var builder = new SpringApplicationBuilder(KisStockBasicInfoAnalysisConfiguration.class).web(WebApplicationType.NONE)
                    .initializers(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
                        @Override
                        public Object postProcessAfterInitialization(Object bean, String beanName) {
                            if (bean instanceof HikariDataSource dataSource) {
                                pool.set(dataSource);
                            }
                            return bean;
                        }
                    }));
            var assertion = assertThatThrownBy(() -> builder.run(arguments(settings)));
            if (failure.equals("missing") || failure.startsWith("future")) {
                assertion.isExactlyInstanceOf(NoSuchElementException.class)
                        .hasStackTraceContaining("KIS stock basic info observation not found. symbol=");
            } else if (failure.equals("corrupt-hash")) {
                assertion.hasStackTraceContaining("Stored KIS stock basic info content integrity check failed. id=29");
            } else if (failure.equals("corrupt-master")) {
                assertion.hasStackTraceContaining("File bytes or SHA-256 do not match the manifest.");
            } else {
                assertion.isExactlyInstanceOf(IllegalArgumentException.class);
            }
            assertThat(pool.get()).isNotNull();
            assertThat(pool.get().isClosed()).isTrue();
            assertThat(snapshotObservationRows(connection)).isEqualTo(rowsBefore);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM analysis_sentinel WHERE id = 41")).isEqualTo(1);
            assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")).isEqualTo(2);
        }
        assertFilesUnchanged(filesBefore);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE,
                "synthetic-private-body", "synthetic-unrelated-private-key");
        assertThat(root.resolve("forbidden-schema.sql")).doesNotExist();
    }

    private Map<String, String> symbolSettings(String url, UUID collectionId, String symbol, KisStockRestrictionFreshnessRequest request) {
        var settings = settings(url, collectionId);
        settings.remove(PREFIX + "observation-id");
        settings.put(PREFIX + "symbol", symbol);
        settings.put(PREFIX + "include-market-warnings", "true");
        settings.put(PREFIX + "warning-market", "KOSDAQ");
        settings.put(PREFIX + "run-precheck", "true");
        addFreshnessSettings(settings, request);
        return settings;
    }

    private List<List<Object>> snapshotObservationRows(Connection connection) throws SQLException {
        var result = new ArrayList<List<Object>>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM kis_stock_basic_info_observation ORDER BY id")) {
            while (rows.next()) {
                byte[] content = rows.getBytes("raw_content");
                result.add(List.of(rows.getLong("id"), rows.getString("requested_symbol"), rows.getInt("http_status"),
                        rows.getTimestamp("request_started_at").toInstant(), rows.getTimestamp("response_received_at").toInstant(),
                        rows.getTimestamp("recorded_at").toInstant(), rows.getInt("content_length"), rows.getString("content_sha256"),
                        content.length, sha256(content)));
            }
        }
        return List.copyOf(result);
    }

    private static Stream<Arguments> freshnessCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(KisStockRestrictionFreshnessStatus.values()).map(status -> Arguments.of(market, status)));
    }

    private void addFreshnessSettings(Map<String, String> settings, KisStockRestrictionFreshnessRequest request) {
        settings.put(PREFIX + "check-freshness", "true");
        settings.put(PREFIX + "evaluated-at", request.evaluatedAt().toString());
        settings.put(PREFIX + "max-master-age", request.maxMasterAge().toString());
        settings.put(PREFIX + "max-basic-info-age", request.maxBasicInfoAge().toString());
    }

    private void assertDisabled(ApplicationContext context) {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(EntityManagerFactory.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoObservationStore.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoObservationRepository.class)).isEmpty();
        assertThat(context.getBeansOfType(StockMasterBatchParsingService.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockBasicInfoAnalysisService.class)).isEmpty();
        assertThat(context.getBeansOfType(ApplicationRunner.class)).isEmpty();
        assertNoMarketWarningBeans(context);
        assertNoUnrelatedBeans(context);
    }

    private void assertNoMarketWarningBeans(ApplicationContext context) {
        assertThat(context.getBeansOfType(KisStockMasterMarketWarningParser.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockMarketWarningObservationPolicy.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionAnalysisService.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionFreshnessPolicy.class)).isEmpty();
        assertNoPrecheckBeans(context);
    }

    private void assertNoPrecheckBeans(ApplicationContext context) {
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckPolicy.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckService.class)).isEmpty();
        assertThat(context.getBeansOfType(KisStockRestrictionPrecheckQueryService.class)).isEmpty();
    }

    private void assertNoUnrelatedBeans(ApplicationContext context) {
        for (Class<?> type : List.of(StockAgentHarnessApplication.class, InvestmentHarness.class, InvestmentAgent.class,
                HarnessScheduler.class, BrokerOrderProvider.class, BrokerOrderCancellationProvider.class, KisCashOrderClient.class,
                KisTokenProvider.class, KisTokenClient.class, KisStockBasicInfoProvider.class, KisStockBasicInfoClient.class,
                KisStockBasicInfoCollectionService.class, KisStockMasterClient.class, OpenAiResponsesClient.class,
                HttpClient.class, TaskScheduler.class, ScheduledAnnotationBeanPostProcessor.class, WebServerFactory.class,
                Flyway.class, DataSourceScriptDatabaseInitializer.class)) {
            assertThat(context.getBeansOfType(type)).as("Unexpected bean type %s", type.getName()).isEmpty();
        }
    }

    private void assertOnlyObservationEntity(EntityManagerFactory factory) {
        assertThat(factory.getMetamodel().getEntities()).singleElement()
                .satisfies(entity -> assertThat(entity.getJavaType()).isEqualTo(KisStockBasicInfoObservationEntity.class));
    }

    private Map<String, String> settings(String url, UUID collectionId) {
        var values = new LinkedHashMap<String, String>();
        values.put(PREFIX + "enabled", "true");
        values.put(PREFIX + "observation-id", "17");
        values.put(PREFIX + "observation-root", root.toString());
        values.put(PREFIX + "collection-id", collectionId.toString());
        values.put("spring.datasource.url", url);
        values.put("spring.datasource.username", "sa");
        values.put("spring.datasource.password", "");
        values.put("spring.datasource.driver-class-name", "org.h2.Driver");
        values.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        values.put("spring.jpa.generate-ddl", "true");
        for (String suffix : List.of("", ".orm")) {
            values.put("spring.jpa.properties.hibernate.hbm2ddl.auto" + suffix, "update");
            for (String prefix : List.of("jakarta.persistence", "javax.persistence")) {
                values.put("spring.jpa.properties." + prefix + ".schema-generation.database.action" + suffix, "drop-and-create");
                values.put("spring.jpa.properties." + prefix + ".schema-generation.scripts.action" + suffix, "drop-and-create");
                values.put("spring.jpa.properties." + prefix + ".schema-generation.scripts.create-target" + suffix,
                        root.resolve("forbidden-schema.sql").toString());
            }
        }
        values.put("spring.flyway.enabled", "true");
        values.put("spring.sql.init.mode", "always");
        values.put("spring.sql.init.schema-locations", "classpath:db/migration/V7__create_kis_stock_basic_info_observation.sql");
        values.put("broker.kis.enabled", "true");
        values.put("market.stock.basic-info.kis.enabled", "true");
        values.put("market.stock.basic-info.kis.app-key", "");
        values.put("market.stock.basic-info.kis.app-secret", "");
        values.put("broker.kis.app-key", "synthetic-unrelated-private-key");
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
        return "jdbc:h2:mem:kis_basic_info_analysis_" + UUID.randomUUID();
    }

    private Connection preparedDatabase(byte[] bytes) throws SQLException {
        return preparedDatabase(KisStockBasicInfoObservationFixture.response(bytes));
    }

    private Connection preparedDatabase(KisStockBasicInfoRawResponse original) throws SQLException {
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
                    CREATE TABLE analysis_sentinel (id INTEGER PRIMARY KEY);
                    INSERT INTO analysis_sentinel VALUES (41);
                    """.getBytes(StandardCharsets.UTF_8)));
            insertObservation(connection, 17L, original, RECORDED_AT);
            return connection;
        } catch (RuntimeException | SQLException failure) {
            connection.close();
            throw failure;
        }
    }

    private void insertObservation(Connection connection, long id, KisStockBasicInfoRawResponse original, Instant recordedAt) throws SQLException {
        var row = KisStockBasicInfoObservationEntity.from(original, recordedAt);
        try (var statement = connection.prepareStatement("""
                INSERT INTO kis_stock_basic_info_observation
                (id, requested_symbol, http_status, request_started_at, response_received_at, recorded_at,
                 content_length, content_sha256, raw_content) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, id);
            statement.setString(2, row.getRequestedSymbol());
            statement.setInt(3, row.getHttpStatus());
            statement.setObject(4, row.getRequestStartedAt().atOffset(ZoneOffset.UTC));
            statement.setObject(5, row.getResponseReceivedAt().atOffset(ZoneOffset.UTC));
            statement.setObject(6, row.getRecordedAt().atOffset(ZoneOffset.UTC));
            statement.setInt(7, row.getContentLength());
            statement.setString(8, row.getContentSha256());
            statement.setBytes(9, row.getRawContent());
            statement.executeUpdate();
        }
    }

    private void assertDatabasePreserved(Connection connection, byte[] bytes) throws SQLException {
        assertDatabasePreserved(connection, KisStockBasicInfoObservationFixture.response(bytes));
    }

    private void assertDatabasePreserved(Connection connection, KisStockBasicInfoRawResponse original) throws SQLException {
        byte[] bytes = original.content();
        assertThat(queryInt(connection, "SELECT COUNT(*) FROM analysis_sentinel WHERE id = 41")).isEqualTo(1);
        assertThat(queryInt(connection, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")).isEqualTo(2);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM kis_stock_basic_info_observation")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(17L);
            assertThat(rows.getString("requested_symbol")).isEqualTo(original.requestedSymbol());
            assertThat(rows.getInt("http_status")).isEqualTo(200);
            var normalized = normalized(original);
            assertThat(rows.getTimestamp("request_started_at").toInstant()).isEqualTo(normalized.requestStartedAt());
            assertThat(rows.getTimestamp("response_received_at").toInstant()).isEqualTo(normalized.responseReceivedAt());
            assertThat(rows.getTimestamp("recorded_at").toInstant()).isEqualTo(RECORDED_AT.truncatedTo(ChronoUnit.MICROS));
            assertThat(rows.getInt("content_length")).isEqualTo(bytes.length);
            assertThat(rows.getString("content_sha256")).isEqualTo(sha256(bytes));
            assertThat(rows.getBytes("raw_content")).isEqualTo(bytes);
            assertThat(rows.next()).isFalse();
        }
    }

    private int queryInt(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    private StockMasterCollectionResult collectMaster() throws IOException {
        return StockMasterBatchParsingFixture.collect(root, KisStockMasterParsingFixture.content(KisStockMasterParsingFixture.row(KOSPI)),
                KisStockMasterParsingFixture.content(KisStockMasterParsingFixture.row(KOSDAQ, SYMBOL, "KR70004Y0000", "SYNTHETIC ALPHA")));
    }

    private StockMasterCollectionResult collectMaster(KisStockMasterMarket market, String code, String preannouncement) throws IOException {
        byte[] kospi = KisStockMasterParsingFixture.row(KOSPI);
        byte[] kosdaq = KisStockMasterParsingFixture.row(KOSDAQ, SYMBOL, "KR70004Y0000", "SYNTHETIC ALPHA");
        byte[] selected = market == KOSPI ? kospi : kosdaq;
        KisStockMasterParsingFixture.put(selected, market == KOSPI ? 124 : 119, code);
        KisStockMasterParsingFixture.put(selected, market == KOSPI ? 126 : 121, preannouncement);
        return StockMasterBatchParsingFixture.collect(root, KisStockMasterParsingFixture.content(kospi), KisStockMasterParsingFixture.content(kosdaq));
    }

    private static Stream<Arguments> combinedCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(
                Arguments.of(market, "00", "N"), Arguments.of(market, "02", "N"),
                Arguments.of(market, "00", "Y"), Arguments.of(market, "99", "N"), Arguments.of(market, "02", "?")));
    }

    private Map<Path, byte[]> snapshotFiles() throws IOException {
        var files = new LinkedHashMap<Path, byte[]>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(path, Files.readAllBytes(path));
            }
        }
        return files;
    }

    private void assertFilesUnchanged(Map<Path, byte[]> before) throws IOException {
        var after = snapshotFiles();
        assertThat(after.keySet()).isEqualTo(before.keySet());
        assertThat(after).hasSize(7);
        for (var entry : before.entrySet()) {
            assertThat(after.get(entry.getKey())).isEqualTo(entry.getValue());
        }
    }
}
