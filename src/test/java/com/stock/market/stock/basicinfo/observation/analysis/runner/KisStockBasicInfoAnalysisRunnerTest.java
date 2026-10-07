package com.stock.market.stock.basicinfo.observation.analysis.runner;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisProperties;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.batch;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.fields;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class KisStockBasicInfoAnalysisRunnerTest {
    private static final String COMPLETED_MESSAGE = "Stored stock basic info analysis complete.";
    private static final String RESTRICTION_COMPLETED_MESSAGE = "Stored stock restriction analysis complete.";
    private static final String FRESHNESS_COMPLETED_MESSAGE = "Stored stock restriction freshness check complete.";
    private static final String PRECHECK_COMPLETED_MESSAGE = "Stored stock restriction precheck complete.";
    private static final Path ROOT = Path.of("unused preserved masters");
    private final StockMasterBatchParsingService masterParser = mock(StockMasterBatchParsingService.class);
    private final KisStockBasicInfoAnalysisService service = mock(KisStockBasicInfoAnalysisService.class);
    private final KisStockMasterMarketWarningParser warningParser = mock(KisStockMasterMarketWarningParser.class);
    private final KisStockMarketWarningObservationPolicy warningPolicy = mock(KisStockMarketWarningObservationPolicy.class);
    private final KisStockRestrictionAnalysisService restrictionService = mock(KisStockRestrictionAnalysisService.class);
    private final KisStockRestrictionFreshnessPolicy freshnessPolicy = mock(KisStockRestrictionFreshnessPolicy.class);
    private final KisStockRestrictionPrecheckService precheckService = mock(KisStockRestrictionPrecheckService.class);
    private final StockMasterBatchParseResult master = batch();
    private final KisStockBasicInfoAnalysisProperties properties = new KisStockBasicInfoAnalysisProperties(
            true, 17L, ROOT.toString(), master.collection().collectionId());
    private final KisStockBasicInfoAnalysisRunner runner = new KisStockBasicInfoAnalysisRunner(masterParser, service, properties);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsNullDependenciesWithoutParsingOrAnalyzing(int index) {
        String[] names = {"masterParser", "service", "properties"};
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisRunner(index == 0 ? null : masterParser,
                index == 1 ? null : service, index == 2 ? null : properties))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(names[index] + " must not be null.");
        verifyNoInteractions(masterParser, service);
    }

    @Test
    void constructionDoesNotReadFilesAnalyzeOrLogCompletion(CapturedOutput output) {
        verifyNoInteractions(masterParser, service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void disabledRunnerDoesNotReadFilesOrAnalyzeEvenWithUnusedInvalidInputs(CapturedOutput output) throws IOException {
        new KisStockBasicInfoAnalysisRunner(masterParser, service,
                new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null)).run(new DefaultApplicationArguments());

        verifyNoInteractions(masterParser, service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "Y", " "})
    void parsesTheSelectedBatchOnceThenAnalyzesTheSelectedIdAndLogsOnlySummary(String suspension, CapturedOutput output) throws IOException {
        var original = response(SYMBOL, fields().put("tr_stop_yn", suspension).put("prdt_name", "synthetic-private-name"));
        var result = new KisStockBasicInfoAnalysisResult(17L, original, screening(master, original));
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenReturn(master);
        when(service.analyze(17L, master)).thenReturn(result);

        runner.run(new DefaultApplicationArguments());

        var order = inOrder(masterParser, service);
        order.verify(masterParser).parseBatch(ROOT, properties.collectionId());
        order.verify(service).analyze(eq(17L), same(master));
        verifyNoMoreInteractions(masterParser, service);
        assertThat(output).contains(COMPLETED_MESSAGE, "observationId=17", "collectionId=" + properties.collectionId(),
                        "symbol=" + SYMBOL, "matchReason=STANDARD_CODE_AND_MARKET_MATCH", "referenceSecurityType=COMMON_STOCK",
                        "restrictionStatus=" + result.screeningResult().status())
                .doesNotContain("synthetic-private-name", "pdno", "msg1", "rawRecord=", "masterBatch=", "AS_OF_VERIFIED",
                        "access_token", "appkey", "appsecret");
    }

    @Test
    void masterFailureStopsBeforeAnalysisWithoutRetryOrSuccessLog(CapturedOutput output) throws IOException {
        var failure = new IOException("Synthetic master validation failure.");
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenThrow(failure);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verifyNoMoreInteractions(masterParser);
        verifyNoInteractions(service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void missingParsedMasterStopsBeforeAnalysisOrSuccessLog(CapturedOutput output) throws IOException {
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Parsed master batch must not be null.");

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verifyNoMoreInteractions(masterParser);
        verifyNoInteractions(service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void differentParsedCollectionStopsBeforeAnalysisOrSuccessLog(CapturedOutput output) throws IOException {
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenReturn(otherMaster());

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Parsed master batch must preserve the requested collection ID.");

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verifyNoMoreInteractions(masterParser);
        verifyNoInteractions(service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @MethodSource("analysisFailures")
    void analysisFailurePropagatesWithoutRetryOrSuccessLog(RuntimeException failure, CapturedOutput output) throws IOException {
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenReturn(master);
        when(service.analyze(17L, master)).thenThrow(failure);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verify(service).analyze(17L, master);
        verifyNoMoreInteractions(masterParser, service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void missingAnalysisResultFailsWithoutSuccessLog(CapturedOutput output) throws IOException {
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenReturn(master);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Analysis result must not be null.");

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verify(service).analyze(17L, master);
        verifyNoMoreInteractions(masterParser, service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"observation", "master"})
    void resultForAnotherObservationOrMasterFailsWithoutSuccessLog(String mismatch, CapturedOutput output) throws IOException {
        var original = response();
        var result = new KisStockBasicInfoAnalysisResult(mismatch.equals("observation") ? 18L : 17L, original,
                screening(mismatch.equals("master") ? otherMaster() : master, original));
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenReturn(master);
        when(service.analyze(17L, master)).thenReturn(result);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Analysis result must preserve the requested observation ID and parsed master batch.");

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verify(service).analyze(17L, master);
        verifyNoMoreInteractions(masterParser, service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE);
    }

    @Test
    void basicModeDoesNotInvokeWarningDependenciesOrEmitCombinedCompletion(CapturedOutput output) throws IOException {
        var original = response();
        var result = new KisStockBasicInfoAnalysisResult(17L, original, screening(master, original));
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenReturn(master);
        when(service.analyze(17L, master)).thenReturn(result);

        new KisStockBasicInfoAnalysisRunner(masterParser, service, properties, warningParser, warningPolicy, restrictionService)
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(warningParser, warningPolicy, restrictionService);
        assertThat(output).contains(COMPLETED_MESSAGE).doesNotContain(RESTRICTION_COMPLETED_MESSAGE);
    }

    @Test
    void disabledCombinedRunnerDoesNotRequireDependenciesOrTouchFilesAndObservations(CapturedOutput output) throws IOException {
        new KisStockBasicInfoAnalysisRunner(masterParser, service,
                new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null, true, null))
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(masterParser, service, warningParser, warningPolicy, restrictionService);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void activeCombinedRunnerRequiresAllWarningDependencies(int index) {
        var settings = combinedProperties(master, KOSDAQ);
        String[] names = {"marketWarningParser", "marketWarningObservationPolicy", "restrictionAnalysisService"};

        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisRunner(masterParser, service, settings,
                index == 0 ? null : warningParser, index == 1 ? null : warningPolicy, index == 2 ? null : restrictionService))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(names[index] + " must not be null.");
        verifyNoInteractions(masterParser, service, warningParser, warningPolicy, restrictionService);
    }

    @Test
    void legacyConstructorDoesNotSilentlyFallBackToBasicAnalysisForCombinedSettings() {
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisRunner(masterParser, service, combinedProperties(master, KOSDAQ)))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("marketWarningParser must not be null.");
    }

    @ParameterizedTest
    @MethodSource("combinedCases")
    void combinedModePreparesTheExplicitMarketOnceBeforeAnalysisAndLogsBothSummaries(
            KisStockMasterMarket market, String code, String preannouncement, CapturedOutput output
    ) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs(market, code, preannouncement, Map.of(), Map.of());
        var expected = prepareCombinedAnalysis(input, market);

        combinedRunner(combinedProperties(input.master(), market)).run(new DefaultApplicationArguments());

        var parsed = input.warnings().source();
        var order = inOrder(masterParser, warningParser, warningPolicy, restrictionService);
        order.verify(masterParser).parseBatch(ROOT, input.master().collection().collectionId());
        order.verify(warningParser).parse(same(parsed.source()));
        order.verify(warningPolicy).evaluate(same(parsed));
        order.verify(restrictionService).analyze(eq(17L), same(input.master()), same(input.warnings()));
        verifyNoMoreInteractions(masterParser, warningParser, warningPolicy, restrictionService);
        verifyNoInteractions(service);
        assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, "observationId=17", "symbol=" + input.response().requestedSymbol(),
                        "warningMarket=" + market, "warningInputSha256=" + parsed.source().inputSha256(),
                        "warningParserVersion=" + parsed.parserVersion(), "warningObservationVersion=" + input.warnings().observationVersion(),
                        "combinedScreeningVersion=" + expected.restrictionScreeningResult().screeningVersion(),
                        "restrictionStatus=" + expected.basicInfoAnalysis().screeningResult().status(),
                        "combinedRestrictionStatus=" + expected.restrictionScreeningResult().status(),
                        "combinedRestrictionReasons=" + expected.restrictionScreeningResult().reasonCodes())
                .doesNotContain("pdno", "msg1", "rawLine=", "masterBatch=", "marketWarningObservation=", "AS_OF_VERIFIED", "appkey", "appsecret");
    }

    @Test
    void explicitlyWrongMarketProducesReviewRatherThanBeingReplacedOrTreatedAsApproval(CapturedOutput output) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs(KOSDAQ, "03", "Y", Map.of(), Map.of());
        var warnings = KisStockRestrictionScreeningFixture.warnings(input.master(), KOSPI);
        var prepared = new KisStockRestrictionAnalysisFixture.Inputs(input.response(), input.master(), warnings);
        var expected = prepareCombinedAnalysis(prepared, KOSPI);

        combinedRunner(combinedProperties(input.master(), KOSPI)).run(new DefaultApplicationArguments());

        assertThat(output).contains(RESTRICTION_COMPLETED_MESSAGE, "warningMarket=KOSPI", "combinedRestrictionStatus=REVIEW_REQUIRED",
                        "combinedRestrictionReasons=" + expected.restrictionScreeningResult().reasonCodes())
                .doesNotContain("MARKET_WARNING_INVESTMENT_RISK_OBSERVED", "MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED");
        verify(restrictionService).analyze(17L, input.master(), warnings);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parse", "observation", "analysis"})
    void combinedStageFailurePropagatesWithoutRetryOrCompletion(String stage, CapturedOutput output) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs();
        prepareCombinedAnalysis(input, KOSDAQ);
        var failure = new IllegalArgumentException("Synthetic combined stage failure.");
        switch (stage) {
            case "parse" -> when(warningParser.parse(any())).thenThrow(failure);
            case "observation" -> when(warningPolicy.evaluate(any())).thenThrow(failure);
            case "analysis" -> when(restrictionService.analyze(any(), any(), any())).thenThrow(failure);
            default -> throw new IllegalArgumentException("Unexpected stage.");
        }

        assertThatThrownBy(() -> combinedRunner(combinedProperties(input.master(), KOSDAQ)).run(new DefaultApplicationArguments()))
                .isSameAs(failure);

        verify(masterParser).parseBatch(ROOT, input.master().collection().collectionId());
        verify(warningParser).parse(input.warnings().source().source());
        if (!stage.equals("parse")) {
            verify(warningPolicy).evaluate(input.warnings().source());
        } else {
            verifyNoInteractions(warningPolicy);
        }
        if (stage.equals("analysis")) {
            verify(restrictionService).analyze(17L, input.master(), input.warnings());
        } else {
            verifyNoInteractions(restrictionService);
        }
        verifyNoMoreInteractions(masterParser, warningParser, warningPolicy, restrictionService);
        verifyNoInteractions(service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parse", "observation", "analysis"})
    void nullCombinedStageResultsFailWithoutCompletion(String stage, CapturedOutput output) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs();
        prepareCombinedAnalysis(input, KOSDAQ);
        switch (stage) {
            case "parse" -> when(warningParser.parse(any())).thenReturn(null);
            case "observation" -> when(warningPolicy.evaluate(any())).thenReturn(null);
            case "analysis" -> when(restrictionService.analyze(any(), any(), any())).thenReturn(null);
            default -> throw new IllegalArgumentException("Unexpected stage.");
        }
        String message = switch (stage) {
            case "parse" -> "Parsed market warnings must not be null.";
            case "observation" -> "Market warning observation must not be null.";
            default -> "Restriction analysis result must not be null.";
        };

        assertThatThrownBy(() -> combinedRunner(combinedProperties(input.master(), KOSDAQ)).run(new DefaultApplicationArguments()))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(message);

        verifyNoInteractions(service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parse", "observation"})
    void wrongWarningSourcesFailBeforeObservationAnalysis(String stage, CapturedOutput output) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs();
        prepareCombinedAnalysis(input, KOSDAQ);
        var other = KisStockRestrictionScreeningFixture.warnings(input.master(), KOSPI);
        if (stage.equals("parse")) {
            when(warningParser.parse(any())).thenReturn(other.source());
        } else {
            when(warningPolicy.evaluate(any())).thenReturn(other);
        }

        assertThatThrownBy(() -> combinedRunner(combinedProperties(input.master(), KOSDAQ)).run(new DefaultApplicationArguments()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(stage.equals("parse") ? "Parsed market warnings must preserve the selected master source."
                        : "Market warning observation must preserve the parsed warning source.");

        verifyNoInteractions(service, restrictionService);
        if (stage.equals("parse")) {
            verifyNoInteractions(warningPolicy);
        }
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"observation", "master", "warning"})
    void unrelatedCombinedAnalysisResultsFailBeforeEitherCompletionLog(String mismatch, CapturedOutput output) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs();
        prepareCombinedAnalysis(input, KOSDAQ);
        var warnings = mismatch.equals("warning") ? KisStockRestrictionScreeningFixture.warnings(input.master(), KOSPI) : input.warnings();
        var basic = new KisStockBasicInfoAnalysisResult(mismatch.equals("observation") ? 18L : 17L, input.response(),
                screening(mismatch.equals("master") ? otherMaster(input.master()) : input.master(), input.response()));
        var wrong = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warnings));
        when(restrictionService.analyze(any(), any(), any())).thenReturn(wrong);

        assertThatThrownBy(() -> combinedRunner(combinedProperties(input.master(), KOSDAQ)).run(new DefaultApplicationArguments()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(mismatch.equals("warning") ? "Restriction analysis must preserve the prepared market warning observation."
                        : "Analysis result must preserve the requested observation ID and parsed master batch.");

        verifyNoInteractions(service);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @Test
    void combinedMasterFailureStopsBeforeAnyWarningOrObservationAnalysis(CapturedOutput output) throws IOException {
        var failure = new IOException("Synthetic master validation failure.");
        when(masterParser.parseBatch(ROOT, properties.collectionId())).thenThrow(failure);

        assertThatThrownBy(() -> combinedRunner(combinedProperties(master, KOSDAQ)).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(masterParser).parseBatch(ROOT, properties.collectionId());
        verifyNoMoreInteractions(masterParser);
        verifyNoInteractions(service, warningParser, warningPolicy, restrictionService);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE);
    }

    @Test
    void activeFreshnessRequiresItsPolicyEvenThroughTheLegacyCombinedConstructor() {
        var settings = freshnessProperties(master, KOSDAQ);

        assertThatThrownBy(() -> combinedRunner(settings)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("restrictionFreshnessPolicy must not be null.");

        verifyNoInteractions(masterParser, service, warningParser, warningPolicy, restrictionService, freshnessPolicy);
    }

    @Test
    void disabledFreshnessRunnerDoesNotRequirePoliciesOrValidateUnusedTiming(CapturedOutput output) throws IOException {
        new KisStockBasicInfoAnalysisRunner(masterParser, service,
                new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null, true, null, true, null, null, null))
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(masterParser, service, warningParser, warningPolicy, restrictionService, freshnessPolicy);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncheckedModesDoNotUseAnAvailableFreshnessPolicyOrEmitItsLog(boolean includeWarnings, CapturedOutput output) throws IOException {
        var input = KisStockRestrictionAnalysisFixture.inputs();
        var combined = prepareCombinedAnalysis(input, KOSDAQ);
        var settings = new KisStockBasicInfoAnalysisProperties(true, 17L, ROOT.toString(), input.master().collection().collectionId(),
                includeWarnings, includeWarnings ? KOSDAQ : null);
        if (!includeWarnings) {
            when(service.analyze(17L, input.master())).thenReturn(combined.basicInfoAnalysis());
        }

        freshnessRunner(settings).run(new DefaultApplicationArguments());

        verifyNoInteractions(freshnessPolicy);
        assertThat(output).contains(COMPLETED_MESSAGE).doesNotContain(FRESHNESS_COMPLETED_MESSAGE);
        if (includeWarnings) {
            verify(restrictionService).analyze(17L, input.master(), input.warnings());
            verifyNoInteractions(service);
            assertThat(output).contains(RESTRICTION_COMPLETED_MESSAGE);
        } else {
            verify(service).analyze(17L, input.master());
            verifyNoInteractions(warningParser, warningPolicy, restrictionService);
            assertThat(output).doesNotContain(RESTRICTION_COMPLETED_MESSAGE);
        }
    }

    @ParameterizedTest
    @MethodSource("freshnessCases")
    void checksTheSameCombinedResultOnceAndLogsIndependentFreshnessWithoutApprovingIt(
            KisStockMasterMarket market, KisStockRestrictionFreshnessStatus status, CapturedOutput output
    ) throws IOException {
        var input = KisStockRestrictionFreshnessFixture.analysis(market, "02", "N", Map.of(), Map.of());
        if (status == KisStockRestrictionFreshnessStatus.EXPIRED) {
            input = KisStockRestrictionFreshnessFixture.withTimes(input, EVALUATED_AT.minus(MAX_MASTER_AGE), EVALUATED_AT.minus(MAX_MASTER_AGE).plusSeconds(1),
                    EVALUATED_AT.minus(MAX_BASIC_INFO_AGE), EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusSeconds(1));
        } else if (status == KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED) {
            input = KisStockRestrictionFreshnessFixture.withTimes(input, EVALUATED_AT.minusSeconds(300), EVALUATED_AT.minusSeconds(299),
                    EVALUATED_AT.minusSeconds(1), EVALUATED_AT.plusNanos(1));
        }
        var prepared = prepareFreshnessAnalysis(input, market);
        var request = KisStockRestrictionFreshnessFixture.request();
        var expected = new KisStockRestrictionFreshnessPolicy().evaluate(request, prepared);
        when(freshnessPolicy.evaluate(eq(request), same(prepared))).thenReturn(expected);
        var selected = prepared.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();

        freshnessRunner(freshnessProperties(selected, market)).run(new DefaultApplicationArguments());

        var warnings = prepared.restrictionScreeningResult().marketWarningObservation();
        var order = inOrder(masterParser, warningParser, warningPolicy, restrictionService, freshnessPolicy);
        order.verify(masterParser).parseBatch(ROOT, selected.collection().collectionId());
        order.verify(warningParser).parse(same(warnings.source().source()));
        order.verify(warningPolicy).evaluate(same(warnings.source()));
        order.verify(restrictionService).analyze(eq(17L), same(selected), same(warnings));
        order.verify(freshnessPolicy).evaluate(eq(request), same(prepared));
        verifyNoMoreInteractions(masterParser, warningParser, warningPolicy, restrictionService, freshnessPolicy);
        verifyNoInteractions(service);
        assertThat(expected.status()).isEqualTo(status);
        assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE,
                        "evaluatedAt=" + EVALUATED_AT, "maxMasterAge=" + MAX_MASTER_AGE, "maxBasicInfoAge=" + MAX_BASIC_INFO_AGE,
                        "freshnessStatus=" + expected.status(), "freshnessReasons=" + expected.reasonCodes(), "freshnessVersion=" + expected.freshnessVersion(),
                        "combinedRestrictionStatus=EXCLUSION_SIGNAL_OBSERVED")
                .doesNotContain("pdno", "msg1", "rawLine=", "masterBatch=", "marketWarningObservation=", "AS_OF_VERIFIED", "appkey", "appsecret");
        assertThat(output.toString().indexOf(RESTRICTION_COMPLETED_MESSAGE)).isLessThan(output.toString().indexOf(FRESHNESS_COMPLETED_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"failure", "null", "request", "analysis"})
    void freshnessFailureOrDisconnectedReturnDoesNotRetryOrLogFreshnessCompletion(String change, CapturedOutput output) throws IOException {
        var input = KisStockRestrictionFreshnessFixture.analysis();
        var prepared = prepareFreshnessAnalysis(input, KOSDAQ);
        var request = KisStockRestrictionFreshnessFixture.request();
        var failure = new IllegalStateException("Synthetic freshness failure.");
        if (change.equals("failure")) {
            when(freshnessPolicy.evaluate(any(), any())).thenThrow(failure);
        } else if (change.equals("request")) {
            var otherRequest = new KisStockRestrictionFreshnessRequest(EVALUATED_AT.plusNanos(1), MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);
            when(freshnessPolicy.evaluate(any(), any())).thenReturn(new KisStockRestrictionFreshnessPolicy().evaluate(otherRequest, prepared));
        } else if (change.equals("analysis")) {
            var basic = prepared.basicInfoAnalysis();
            var other = new KisStockRestrictionAnalysisResult(new KisStockBasicInfoAnalysisResult(18L, basic.response(), basic.screeningResult()),
                    prepared.restrictionScreeningResult());
            when(freshnessPolicy.evaluate(any(), any())).thenReturn(new KisStockRestrictionFreshnessPolicy().evaluate(request, other));
        }
        var selected = prepared.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var assertion = assertThatThrownBy(() -> freshnessRunner(freshnessProperties(selected, KOSDAQ)).run(new DefaultApplicationArguments()));
        if (change.equals("failure")) {
            assertion.isSameAs(failure);
        } else if (change.equals("null")) {
            assertion.isExactlyInstanceOf(NullPointerException.class).hasMessage("Restriction freshness result must not be null.");
        } else {
            assertion.isExactlyInstanceOf(IllegalStateException.class)
                    .hasMessage("Restriction freshness must preserve the evaluation request and complete analysis result.");
        }

        verify(freshnessPolicy).evaluate(eq(request), same(prepared));
        verifyNoMoreInteractions(freshnessPolicy);
        verify(restrictionService).analyze(17L, selected, prepared.restrictionScreeningResult().marketWarningObservation());
        verifyNoMoreInteractions(restrictionService);
        verifyNoInteractions(service);
        assertThat(output).doesNotContain(FRESHNESS_COMPLETED_MESSAGE);
    }

    @Test
    void combinedAnalysisFailureStopsBeforeFreshnessWithoutRetry(CapturedOutput output) throws IOException {
        var prepared = prepareFreshnessAnalysis(KisStockRestrictionFreshnessFixture.analysis(), KOSDAQ);
        var failure = new IllegalArgumentException("Synthetic combined analysis failure.");
        when(restrictionService.analyze(any(), any(), any())).thenThrow(failure);
        var selected = prepared.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();

        assertThatThrownBy(() -> freshnessRunner(freshnessProperties(selected, KOSDAQ)).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(restrictionService).analyze(17L, selected, prepared.restrictionScreeningResult().marketWarningObservation());
        verifyNoMoreInteractions(restrictionService);
        verifyNoInteractions(service, freshnessPolicy);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE);
    }

    @Test
    void precheckRequiresItsServiceEvenThroughTheLegacyFreshnessConstructor() {
        var settings = precheckProperties(master, KOSDAQ);

        assertThatThrownBy(() -> freshnessRunner(settings)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("restrictionPrecheckService must not be null.");
        verifyNoInteractions(masterParser, service, warningParser, warningPolicy, restrictionService, freshnessPolicy, precheckService);
    }

    @Test
    void disabledPrecheckDoesNotRequireDependenciesOrReadAnything(CapturedOutput output) throws IOException {
        new KisStockBasicInfoAnalysisRunner(masterParser, service,
                new KisStockBasicInfoAnalysisProperties(false, -1L, "bad\0path", null, false, null, false, null, null, null, true))
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(masterParser, service, warningParser, warningPolicy, restrictionService, freshnessPolicy, precheckService);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void oldModesIgnoreAnAvailablePrecheckServiceAndKeepTheirCompletionLogs(int mode, CapturedOutput output) throws IOException {
        var analysis = prepareFreshnessAnalysis(KisStockRestrictionFreshnessFixture.analysis(), KOSDAQ);
        var selected = analysis.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var settings = new KisStockBasicInfoAnalysisProperties(true, 17L, ROOT.toString(), selected.collection().collectionId(),
                mode > 0, mode > 0 ? KOSDAQ : null, mode == 2, EVALUATED_AT, MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);
        if (mode == 0) {
            when(service.analyze(17L, selected)).thenReturn(analysis.basicInfoAnalysis());
        } else if (mode == 2) {
            var request = KisStockRestrictionFreshnessFixture.request();
            when(freshnessPolicy.evaluate(request, analysis)).thenReturn(new KisStockRestrictionFreshnessPolicy().evaluate(request, analysis));
        }

        precheckRunner(settings).run(new DefaultApplicationArguments());

        verifyNoInteractions(precheckService);
        assertThat(output).contains(COMPLETED_MESSAGE).doesNotContain(PRECHECK_COMPLETED_MESSAGE);
        if (mode == 0) {
            verify(service).analyze(17L, selected);
            verifyNoInteractions(warningParser, warningPolicy, restrictionService, freshnessPolicy);
            assertThat(output).doesNotContain(RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE);
        } else {
            verify(restrictionService).analyze(17L, selected, analysis.restrictionScreeningResult().marketWarningObservation());
            verifyNoInteractions(service);
            assertThat(output).contains(RESTRICTION_COMPLETED_MESSAGE);
            if (mode == 2) {
                verify(freshnessPolicy).evaluate(KisStockRestrictionFreshnessFixture.request(), analysis);
                assertThat(output).contains(FRESHNESS_COMPLETED_MESSAGE);
            } else {
                verifyNoInteractions(freshnessPolicy);
                assertThat(output).doesNotContain(FRESHNESS_COMPLETED_MESSAGE);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("precheckCases")
    void precheckUsesOneServiceCallAndReusesAllResultsWithoutDirectAnalysisOrFreshnessCalls(
            KisStockMasterMarket market, String code, KisStockRestrictionFreshnessStatus status, CapturedOutput output
    ) throws IOException {
        var input = KisStockRestrictionFreshnessFixture.analysis(market, code, "N", Map.of(), Map.of("prdt_name", "synthetic-private-name"));
        if (status == KisStockRestrictionFreshnessStatus.EXPIRED) {
            input = KisStockRestrictionFreshnessFixture.withTimes(input, EVALUATED_AT.minus(MAX_MASTER_AGE), EVALUATED_AT.minus(MAX_MASTER_AGE).plusSeconds(1),
                    EVALUATED_AT.minus(MAX_BASIC_INFO_AGE), EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusSeconds(1));
        } else if (status == KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED) {
            input = KisStockRestrictionFreshnessFixture.withTimes(input, EVALUATED_AT.minusSeconds(300), EVALUATED_AT.minusSeconds(299),
                    EVALUATED_AT.minusSeconds(1), EVALUATED_AT.plusNanos(1));
        }
        var analysis = prepareFreshnessAnalysis(input, market);
        var selected = analysis.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var warnings = analysis.restrictionScreeningResult().marketWarningObservation();
        var request = KisStockRestrictionFreshnessFixture.request();
        var freshness = new KisStockRestrictionFreshnessPolicy().evaluate(request, analysis);
        var expected = new KisStockRestrictionPrecheckPolicy().evaluate(freshness);
        when(precheckService.precheck(17L, selected, warnings, request)).thenReturn(expected);

        precheckRunner(precheckProperties(selected, market)).run(new DefaultApplicationArguments());

        var order = inOrder(masterParser, warningParser, warningPolicy, precheckService);
        order.verify(masterParser).parseBatch(ROOT, selected.collection().collectionId());
        order.verify(warningParser).parse(same(warnings.source().source()));
        order.verify(warningPolicy).evaluate(same(warnings.source()));
        order.verify(precheckService).precheck(eq(17L), same(selected), same(warnings), eq(request));
        verifyNoMoreInteractions(masterParser, warningParser, warningPolicy, precheckService);
        verifyNoInteractions(service, restrictionService, freshnessPolicy);
        assertThat(freshness.status()).isEqualTo(status);
        assertThat(output).contains(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE,
                        "observationId=17", "collectionId=" + selected.collection().collectionId(), "symbol=" + analysis.basicInfoAnalysis().response().requestedSymbol(),
                        "evaluatedAt=" + request.evaluatedAt(), "maxMasterAge=" + request.maxMasterAge(), "maxBasicInfoAge=" + request.maxBasicInfoAge(),
                        "combinedRestrictionStatus=" + analysis.restrictionScreeningResult().status(),
                        "combinedRestrictionReasons=" + analysis.restrictionScreeningResult().reasonCodes(),
                        "freshnessStatus=" + freshness.status(), "freshnessReasons=" + freshness.reasonCodes(),
                        "precheckStatus=" + expected.status(), "precheckVersion=" + expected.precheckVersion())
                .doesNotContain("synthetic-private-name", "pdno", "msg1", "rawLine=", "masterBatch=", "marketWarningObservation=", "AS_OF_VERIFIED",
                        "access_token", "appkey", "appsecret");
        for (String message : new String[]{COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE}) {
            assertThat(output.toString().split(java.util.regex.Pattern.quote(message), -1)).hasSize(2);
        }
        assertThat(output.toString().indexOf(FRESHNESS_COMPLETED_MESSAGE)).isLessThan(output.toString().indexOf(PRECHECK_COMPLETED_MESSAGE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"failure", "null", "evaluated-at", "master-age", "basic-info-age", "observation", "master", "warning"})
    void precheckFailureOrDisconnectedReturnStopsWithoutRetryOrAnyCompletion(String change, CapturedOutput output) throws IOException {
        var analysis = prepareFreshnessAnalysis(KisStockRestrictionFreshnessFixture.analysis(), KOSDAQ);
        var selected = analysis.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var warnings = analysis.restrictionScreeningResult().marketWarningObservation();
        var request = KisStockRestrictionFreshnessFixture.request();
        var failure = new IllegalStateException("Synthetic precheck failure.");
        if (change.equals("failure")) {
            when(precheckService.precheck(any(), any(), any(), any())).thenThrow(failure);
        } else if (!change.equals("null")) {
            var changedRequest = new KisStockRestrictionFreshnessRequest(change.equals("evaluated-at") ? EVALUATED_AT.plusNanos(1) : EVALUATED_AT,
                    change.equals("master-age") ? MAX_MASTER_AGE.plusNanos(1) : MAX_MASTER_AGE,
                    change.equals("basic-info-age") ? MAX_BASIC_INFO_AGE.plusNanos(1) : MAX_BASIC_INFO_AGE);
            var basic = analysis.basicInfoAnalysis();
            var changedBasic = new KisStockBasicInfoAnalysisResult(change.equals("observation") ? 18L : 17L, basic.response(),
                    screening(change.equals("master") ? otherMaster(selected) : selected, basic.response()));
            var changedWarnings = change.equals("warning") ? KisStockRestrictionScreeningFixture.warnings(selected, KOSPI) : warnings;
            var changedAnalysis = new KisStockRestrictionAnalysisResult(changedBasic,
                    new KisStockRestrictionScreeningPolicy().evaluate(changedBasic.screeningResult(), changedWarnings));
            var changedResult = new KisStockRestrictionPrecheckPolicy().evaluate(new KisStockRestrictionFreshnessPolicy().evaluate(changedRequest, changedAnalysis));
            when(precheckService.precheck(any(), any(), any(), any())).thenReturn(changedResult);
        }

        var assertion = assertThatThrownBy(() -> precheckRunner(precheckProperties(selected, KOSDAQ)).run(new DefaultApplicationArguments()));
        if (change.equals("failure")) {
            assertion.isSameAs(failure);
        } else if (change.equals("null")) {
            assertion.isExactlyInstanceOf(NullPointerException.class).hasMessage("Restriction precheck result must not be null.");
        } else {
            assertion.isExactlyInstanceOf(IllegalStateException.class);
        }
        verify(precheckService).precheck(eq(17L), same(selected), same(warnings), eq(request));
        verifyNoMoreInteractions(precheckService);
        verifyNoInteractions(service, restrictionService, freshnessPolicy);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parse-failure", "parse-source", "warning-failure", "warning-source"})
    void precheckDoesNotRunWhenWarningPreparationFails(String stage, CapturedOutput output) throws IOException {
        var analysis = prepareFreshnessAnalysis(KisStockRestrictionFreshnessFixture.analysis(), KOSDAQ);
        var selected = analysis.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var other = KisStockRestrictionScreeningFixture.warnings(selected, KOSPI);
        if (stage.equals("parse-failure")) {
            when(warningParser.parse(any())).thenThrow(new IllegalStateException("Synthetic warning parsing failure."));
        } else if (stage.equals("parse-source")) {
            when(warningParser.parse(any())).thenReturn(other.source());
        } else if (stage.equals("warning-failure")) {
            when(warningPolicy.evaluate(any())).thenThrow(new IllegalStateException("Synthetic warning observation failure."));
        } else {
            when(warningPolicy.evaluate(any())).thenReturn(other);
        }

        assertThatThrownBy(() -> precheckRunner(precheckProperties(selected, KOSDAQ)).run(new DefaultApplicationArguments()))
                .isExactlyInstanceOf(IllegalStateException.class);

        verify(warningParser).parse(analysis.restrictionScreeningResult().marketWarningObservation().source().source());
        verifyNoMoreInteractions(warningParser);
        if (stage.startsWith("parse")) {
            verifyNoInteractions(warningPolicy);
        } else {
            verify(warningPolicy).evaluate(analysis.restrictionScreeningResult().marketWarningObservation().source());
            verifyNoMoreInteractions(warningPolicy);
        }
        verifyNoInteractions(service, restrictionService, freshnessPolicy, precheckService);
        assertThat(output).doesNotContain(COMPLETED_MESSAGE, RESTRICTION_COMPLETED_MESSAGE, FRESHNESS_COMPLETED_MESSAGE, PRECHECK_COMPLETED_MESSAGE);
    }

    private KisStockBasicInfoAnalysisRunner precheckRunner(KisStockBasicInfoAnalysisProperties settings) {
        return new KisStockBasicInfoAnalysisRunner(masterParser, service, settings, warningParser, warningPolicy, restrictionService, freshnessPolicy, precheckService);
    }

    private KisStockBasicInfoAnalysisProperties precheckProperties(StockMasterBatchParseResult selected, KisStockMasterMarket market) {
        return new KisStockBasicInfoAnalysisProperties(true, 17L, ROOT.toString(), selected.collection().collectionId(), true, market,
                true, EVALUATED_AT, MAX_MASTER_AGE, MAX_BASIC_INFO_AGE, true);
    }

    private static Stream<Arguments> precheckCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.FRESH),
                Arguments.of(market, "99", KisStockRestrictionFreshnessStatus.FRESH),
                Arguments.of(market, "02", KisStockRestrictionFreshnessStatus.FRESH),
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.EXPIRED),
                Arguments.of(market, "02", KisStockRestrictionFreshnessStatus.EXPIRED),
                Arguments.of(market, "00", KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED)));
    }

    private KisStockBasicInfoAnalysisRunner freshnessRunner(KisStockBasicInfoAnalysisProperties settings) {
        return new KisStockBasicInfoAnalysisRunner(masterParser, service, settings, warningParser, warningPolicy, restrictionService, freshnessPolicy);
    }

    private KisStockBasicInfoAnalysisProperties freshnessProperties(StockMasterBatchParseResult selected, KisStockMasterMarket market) {
        return new KisStockBasicInfoAnalysisProperties(true, 17L, ROOT.toString(), selected.collection().collectionId(), true, market,
                true, EVALUATED_AT, MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);
    }

    private KisStockRestrictionAnalysisResult prepareFreshnessAnalysis(KisStockRestrictionAnalysisResult input, KisStockMasterMarket market) throws IOException {
        var selected = input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        return prepareCombinedAnalysis(new KisStockRestrictionAnalysisFixture.Inputs(input.basicInfoAnalysis().response(), selected,
                input.restrictionScreeningResult().marketWarningObservation()), market);
    }

    private static Stream<Arguments> freshnessCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(KisStockRestrictionFreshnessStatus.values()).map(status -> Arguments.of(market, status)));
    }

    private KisStockBasicInfoAnalysisRunner combinedRunner(KisStockBasicInfoAnalysisProperties settings) {
        return new KisStockBasicInfoAnalysisRunner(masterParser, service, settings, warningParser, warningPolicy, restrictionService);
    }

    private KisStockBasicInfoAnalysisProperties combinedProperties(StockMasterBatchParseResult selected, KisStockMasterMarket market) {
        return new KisStockBasicInfoAnalysisProperties(true, 17L, ROOT.toString(), selected.collection().collectionId(), true, market);
    }

    private KisStockRestrictionAnalysisResult prepareCombinedAnalysis(KisStockRestrictionAnalysisFixture.Inputs input, KisStockMasterMarket market)
            throws IOException {
        var parsed = input.warnings().source();
        assertThat(parsed.source().market()).isEqualTo(market);
        var basic = KisStockRestrictionAnalysisFixture.analysis(17L, input);
        var result = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), input.warnings()));
        when(masterParser.parseBatch(ROOT, input.master().collection().collectionId())).thenReturn(input.master());
        when(warningParser.parse(parsed.source())).thenReturn(parsed);
        when(warningPolicy.evaluate(parsed)).thenReturn(input.warnings());
        when(restrictionService.analyze(17L, input.master(), input.warnings())).thenReturn(result);
        return result;
    }

    private static Stream<Arguments> combinedCases() {
        return Stream.of(KOSPI, KOSDAQ).flatMap(market -> Stream.of(
                Arguments.of(market, "00", "N"), Arguments.of(market, "02", "N"),
                Arguments.of(market, "00", "Y"), Arguments.of(market, "99", "N"), Arguments.of(market, "02", "?")));
    }

    private StockMasterBatchParseResult otherMaster() {
        return otherMaster(master);
    }

    private StockMasterBatchParseResult otherMaster(StockMasterBatchParseResult original) {
        var collection = original.collection();
        return new StockMasterBatchParseResult(new StockMasterCollectionResult(collection.formatVersion(), UUID.randomUUID(),
                collection.evidenceScope(), collection.startedAt(), collection.finishedAt(), collection.files()), original.marketResults());
    }

    private static Stream<RuntimeException> analysisFailures() {
        return Stream.of(new NoSuchElementException("Synthetic missing observation."),
                new IllegalArgumentException("Synthetic parsing failure."), new IllegalStateException("Synthetic integrity failure."));
    }
}
