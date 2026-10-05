package com.stock.market.stock.master.collection.runner;

import com.stock.market.stock.master.collection.StockMasterCollectionService;
import com.stock.market.stock.master.collection.runner.config.StockMasterCollectionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Path;

import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.properties;
import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class StockMasterCollectionRunnerTest {
    @TempDir
    Path directory;
    private final StockMasterCollectionService service = mock(StockMasterCollectionService.class);

    @Test
    void disabledRunnerDoesNotCollect(CapturedOutput output) throws IOException {
        var properties = new StockMasterCollectionProperties(false, null, null, null, null, null);

        new StockMasterCollectionRunner(service, properties).run(new DefaultApplicationArguments());

        verifyNoInteractions(service);
        assertThat(output).doesNotContain("Stock master collection completed.");
    }

    @Test
    void enabledRunnerCallsServiceOnceAndLogsOnlyCompletedCollection(CapturedOutput output) throws IOException {
        when(service.collect(directory)).thenReturn(result());

        new StockMasterCollectionRunner(service, properties(directory)).run(new DefaultApplicationArguments());

        verify(service).collect(directory);
        assertThat(output).contains("Stock master collection completed.", "marketCount=2", result().collectionId().toString())
                .doesNotContain("AS_OF_VERIFIED");
    }

    @Test
    void propagatesFailureWithoutSuccessLogOrRetry(CapturedOutput output) throws IOException {
        when(service.collect(directory)).thenThrow(new IOException("collection failed"));

        assertThatThrownBy(() -> new StockMasterCollectionRunner(service, properties(directory))
                .run(new DefaultApplicationArguments())).isInstanceOf(IOException.class).hasMessage("collection failed");
        verify(service).collect(directory);
        assertThat(output).doesNotContain("Stock master collection completed.");
    }
}
