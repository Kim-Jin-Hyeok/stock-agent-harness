package com.stock.market.stock.master.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;

import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.CONTENT;
import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.NOW;
import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockMasterCollectionServiceTest {
    @TempDir
    Path root;
    private final KisStockMasterClient client = mock(KisStockMasterClient.class);
    private StockMasterCollectionService service;

    @BeforeEach
    void setUp() {
        service = new StockMasterCollectionService(client, Clock.fixed(NOW, ZoneOffset.UTC), 2048);
    }

    @Test
    void preservesBothArchivesAndExtractedBytesAndPublishesRestorableManifest() throws Exception {
        for (var market : KisStockMasterMarket.values()) {
            when(client.download(market)).thenReturn(zip(market.fileName()));
        }

        var result = service.collect(root);

        Path batch = root.resolve(result.collectionId().toString());
        assertThat(result.evidenceScope()).isEqualTo("CURRENT_OBSERVATION");
        assertThat(result.startedAt()).isEqualTo(NOW);
        assertThat(result.finishedAt()).isEqualTo(NOW);
        assertThat(result.files()).extracting(StockMasterCollectionResult.FileObservation::market)
                .containsExactly(KisStockMasterMarket.KOSPI, KisStockMasterMarket.KOSDAQ);
        for (var file : result.files()) {
            byte[] archive = zip(file.market().fileName());
            assertThat(Files.readAllBytes(batch.resolve(file.archivePath()))).isEqualTo(archive);
            assertThat(Files.readAllBytes(batch.resolve(file.extractedPath()))).isEqualTo(CONTENT);
            assertThat(file.archiveSha256()).isEqualTo(sha256(archive));
            assertThat(file.extractedSha256()).isEqualTo(sha256(CONTENT));
            assertThat(file.archiveBytes()).isEqualTo(archive.length);
            assertThat(file.extractedBytes()).isEqualTo(CONTENT.length);
            verify(client).download(file.market());
        }
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        assertThat(mapper.readValue(batch.resolve("manifest.json").toFile(), StockMasterCollectionResult.class))
                .isEqualTo(result);
        for (var file : result.files()) {
            assertThat(mapper.readValue(batch.resolve(file.market().name()).resolve("observation.json").toFile(),
                    StockMasterCollectionResult.FileObservation.class)).isEqualTo(file);
        }
        assertThat(Files.readString(batch.resolve("manifest.json"))).contains(NOW.toString())
                .doesNotContain("AS_OF_VERIFIED", "listingStatus", "informationAvailableAt");
        assertThat(batch.resolve("manifest.json.partial")).doesNotExist();
    }

    @Test
    void repeatedCollectionCreatesNewBatchWithoutChangingPreviousEvidence() throws Exception {
        for (var market : KisStockMasterMarket.values()) {
            when(client.download(market)).thenReturn(zip(market.fileName()));
        }
        var first = service.collect(root);
        Path firstManifest = root.resolve(first.collectionId().toString()).resolve("manifest.json");
        byte[] before = Files.readAllBytes(firstManifest);

        var second = service.collect(root);

        assertThat(second.collectionId()).isNotEqualTo(first.collectionId());
        assertThat(Files.readAllBytes(firstManifest)).isEqualTo(before);
        for (var market : KisStockMasterMarket.values()) {
            verify(client, times(2)).download(market);
        }
    }

    @Test
    void recordsSeparateCollectionAndMarketStartAndFinishTimes() throws IOException {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusNanos(1), NOW.plusNanos(2), NOW.plusNanos(3),
                NOW.plusNanos(4), NOW.plusNanos(5));
        service = new StockMasterCollectionService(client, clock, 2048);
        for (var market : KisStockMasterMarket.values()) {
            when(client.download(market)).thenReturn(zip(market.fileName()));
        }

        var result = service.collect(root);

        assertThat(result.startedAt()).isEqualTo(NOW);
        assertThat(result.finishedAt()).isEqualTo(NOW.plusNanos(5));
        assertThat(result.files().getFirst().startedAt()).isEqualTo(NOW.plusNanos(1));
        assertThat(result.files().getFirst().finishedAt()).isEqualTo(NOW.plusNanos(2));
        assertThat(result.files().getLast().startedAt()).isEqualTo(NOW.plusNanos(3));
        assertThat(result.files().getLast().finishedAt()).isEqualTo(NOW.plusNanos(4));
    }

    @Test
    void firstMarketFailureDoesNotRequestSecondMarketOrPublishManifest() throws IOException {
        when(client.download(KisStockMasterMarket.KOSPI)).thenThrow(new IOException("download failure"));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasMessageContaining("market=KOSPI").hasRootCauseMessage("download failure");
        verify(client, never()).download(KisStockMasterMarket.KOSDAQ);
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
    }

    @Test
    void secondMarketFailureRetainsFirstMarketButNeverPublishesGlobalSuccess() throws IOException {
        byte[] archive = zip(KisStockMasterMarket.KOSPI.fileName());
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(archive);
        when(client.download(KisStockMasterMarket.KOSDAQ)).thenThrow(new IOException("second failure"));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasMessageContaining("market=KOSDAQ");
        Path batch = onlyBatch();
        assertThat(Files.readAllBytes(batch.resolve("KOSPI/kospi_code.mst.zip"))).isEqualTo(archive);
        assertThat(Files.readAllBytes(batch.resolve("KOSPI/kospi_code.mst"))).isEqualTo(CONTENT);
        assertThat(batch.resolve("KOSPI/observation.json")).exists();
        assertThat(batch.resolve("KOSDAQ/observation.json")).doesNotExist();
        assertThat(batch.resolve("manifest.json")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.mst", "C:/escape.mst", "/escape.mst", "folder/kospi_code.mst", "unexpected.mst"})
    void rejectsUnexpectedOrEscapingEntryWithoutExtractingIt(String entry) throws IOException {
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(zip(entry));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Unexpected stock master ZIP entry.");
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
        assertThat(onlyBatch().resolve("KOSPI/kospi_code.mst")).doesNotExist();
        assertThat(root.resolve("escape.mst")).doesNotExist();
    }

    @Test
    void rejectsAdditionalZipEntriesInsteadOfSilentlyIgnoringThem() throws IOException {
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(zip("kospi_code.mst", "other.mst"));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Stock master ZIP must contain exactly one expected file.");
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
    }

    @Test
    void rejectsEmptyZip() throws IOException {
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(zip());

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Stock master ZIP must contain exactly one expected file.");
    }

    @Test
    void rejectsNonZipAndKeepsDownloadedArchiveForDiagnosis() throws IOException {
        byte[] corrupt = new byte[]{0, 1, 2, 3};
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(corrupt);

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(onlyBatch().resolve("KOSPI/kospi_code.mst.zip"))).isEqualTo(corrupt);
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
    }

    @Test
    void rejectsZipMissingEndOfCentralDirectory() throws IOException {
        byte[] archive = zip("kospi_code.mst");
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(Arrays.copyOf(archive, archive.length - 22));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class);
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
        assertThat(onlyBatch().resolve("KOSPI/kospi_code.mst")).doesNotExist();
    }

    @Test
    void rejectsEmptyExpectedMasterFile() throws IOException {
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(zip(new byte[0], "kospi_code.mst"));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Stock master extracted size must be positive and within maxExtractedBytes.");
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
    }

    @Test
    void rejectsAdvertisedExtractedOversize() throws IOException {
        service = new StockMasterCollectionService(client, Clock.fixed(NOW, ZoneOffset.UTC), CONTENT.length - 1);
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(zip("kospi_code.mst"));

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Stock master extracted size must be positive and within maxExtractedBytes.");
        assertThat(onlyBatch().resolve("KOSPI/kospi_code.mst")).doesNotExist();
    }

    @Test
    void enforcesActualInflatedLimitEvenWhenDeclaredSizeIsFalse() throws IOException {
        service = new StockMasterCollectionService(client, Clock.fixed(NOW, ZoneOffset.UTC), 10);
        byte[] archive = zip(new byte[64], "kospi_code.mst");
        changeCentralDirectoryInt(archive, 24, 1);
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(archive);

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Stock master extracted content exceeds maxExtractedBytes.");
        assertThat(onlyBatch().resolve("KOSPI/kospi_code.mst")).doesNotExist();
        assertThat(onlyBatch().resolve("manifest.json")).doesNotExist();
    }

    @Test
    void verifiesCrcBeforePublishingExtractedFile() throws IOException {
        byte[] archive = zip("kospi_code.mst");
        changeCentralDirectoryInt(archive, 16, 0);
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(archive);

        assertThatThrownBy(() -> service.collect(root)).isInstanceOf(IOException.class)
                .hasRootCauseMessage("Stock master ZIP entry size or CRC does not match extracted content.");
        assertThat(onlyBatch().resolve("KOSPI/kospi_code.mst")).doesNotExist();
    }

    @Test
    void acceptsExactExtractedLimit() throws IOException {
        service = new StockMasterCollectionService(client, Clock.fixed(NOW, ZoneOffset.UTC), CONTENT.length);
        for (var market : KisStockMasterMarket.values()) {
            when(client.download(market)).thenReturn(zip(market.fileName()));
        }

        assertThat(service.collect(root).files()).hasSize(2);
    }

    @Test
    void rejectsFileAsOutputDirectoryBeforeAnyDownload() throws IOException {
        Path file = Files.createFile(root.resolve("file"));

        assertThatThrownBy(() -> service.collect(file)).isInstanceOf(IOException.class);
        verifyNoInteractions(client);
    }

    private Path onlyBatch() throws IOException {
        try (var entries = Files.list(root)) {
            var batches = entries.toList();
            assertThat(batches).hasSize(1);
            return batches.getFirst();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void changeCentralDirectoryInt(byte[] archive, int offset, int value) {
        ByteBuffer buffer = ByteBuffer.wrap(archive).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index <= archive.length - 46; index++) {
            if (buffer.getInt(index) == 0x02014b50) {
                buffer.putInt(index + offset, value);
                return;
            }
        }
        throw new IllegalArgumentException("Fixture must contain a ZIP central directory.");
    }
}
