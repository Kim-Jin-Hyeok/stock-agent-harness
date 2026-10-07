package com.stock.market.stock.basicinfo.observation.analysis.runner;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.runner.config.KisStockBasicInfoAnalysisProperties;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.parsing.StockMasterBatchParsingService;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.batch;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.fields;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
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
    private static final Path ROOT = Path.of("unused preserved masters");
    private final StockMasterBatchParsingService masterParser = mock(StockMasterBatchParsingService.class);
    private final KisStockBasicInfoAnalysisService service = mock(KisStockBasicInfoAnalysisService.class);
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

    private StockMasterBatchParseResult otherMaster() {
        var collection = master.collection();
        return new StockMasterBatchParseResult(new StockMasterCollectionResult(collection.formatVersion(), UUID.randomUUID(),
                collection.evidenceScope(), collection.startedAt(), collection.finishedAt(), collection.files()), master.marketResults());
    }

    private static Stream<RuntimeException> analysisFailures() {
        return Stream.of(new NoSuchElementException("Synthetic missing observation."),
                new IllegalArgumentException("Synthetic parsing failure."), new IllegalStateException("Synthetic integrity failure."));
    }
}
