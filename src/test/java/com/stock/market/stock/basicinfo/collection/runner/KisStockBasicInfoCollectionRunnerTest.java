package com.stock.market.stock.basicinfo.collection.runner;

import com.stock.market.stock.basicinfo.collection.KisStockBasicInfoCollectionService;
import com.stock.market.stock.basicinfo.collection.runner.config.KisStockBasicInfoCollectionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class KisStockBasicInfoCollectionRunnerTest {
    private static final String SYMBOL = "0004Y0";
    private static final String SAVED_MESSAGE = "Stock basic info raw observation saved.";
    private final KisStockBasicInfoCollectionService service = mock(KisStockBasicInfoCollectionService.class);

    @Test
    void rejectsNullDependenciesWithoutCollecting() {
        assertThatThrownBy(() -> new KisStockBasicInfoCollectionRunner(null, enabledProperties()))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("service must not be null.");
        assertThatThrownBy(() -> new KisStockBasicInfoCollectionRunner(service, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("properties must not be null.");
        verifyNoInteractions(service);
    }

    @Test
    void disabledRunnerDoesNotCollectOrLogSavedObservation(CapturedOutput output) {
        var properties = new KisStockBasicInfoCollectionProperties(false, null);

        new KisStockBasicInfoCollectionRunner(service, properties).run(new DefaultApplicationArguments());

        verifyNoInteractions(service);
        assertThat(output).doesNotContain(SAVED_MESSAGE);
    }

    @Test
    void constructionDoesNotCollectOrLogSavedObservation(CapturedOutput output) {
        new KisStockBasicInfoCollectionRunner(service, enabledProperties());

        verifyNoInteractions(service);
        assertThat(output).doesNotContain(SAVED_MESSAGE);
    }

    @Test
    void enabledRunnerCollectsOnceAndLogsOnlySymbolAndPositiveObservationId(CapturedOutput output) {
        when(service.collect(SYMBOL)).thenReturn(17L);

        new KisStockBasicInfoCollectionRunner(service, enabledProperties()).run(new DefaultApplicationArguments());

        verify(service).collect(SYMBOL);
        verifyNoMoreInteractions(service);
        assertThat(output).contains(SAVED_MESSAGE, "symbol=0004Y0", "observationId=17")
                .doesNotContain("AS_OF_VERIFIED", "ELIGIBLE", "rt_cd", "access_token", "appkey", "appsecret");
    }

    @Test
    void collectionFailurePropagatesWithoutRetryOrSavedObservationLog(CapturedOutput output) {
        var failure = new IllegalStateException("Synthetic collection failure.");
        when(service.collect(SYMBOL)).thenThrow(failure);

        assertThatThrownBy(() -> new KisStockBasicInfoCollectionRunner(service, enabledProperties())
                .run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(service).collect(SYMBOL);
        verifyNoMoreInteractions(service);
        assertThat(output).doesNotContain(SAVED_MESSAGE, "observationId=");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void invalidSavedIdFailsWithoutRetryOrSavedObservationLog(Long id, CapturedOutput output) {
        when(service.collect(SYMBOL)).thenReturn(id);

        assertThatThrownBy(() -> new KisStockBasicInfoCollectionRunner(service, enabledProperties())
                .run(new DefaultApplicationArguments()))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Saved observationId must be positive.");

        verify(service).collect(SYMBOL);
        verifyNoMoreInteractions(service);
        assertThat(output).doesNotContain(SAVED_MESSAGE, "observationId=");
    }

    private KisStockBasicInfoCollectionProperties enabledProperties() {
        return new KisStockBasicInfoCollectionProperties(true, SYMBOL);
    }
}
