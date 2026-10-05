package com.stock.market.stock.master.parsing;

import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.batch;
import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.changeObservation;
import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.collect;
import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.manifest;
import static com.stock.market.stock.master.parsing.support.StockMasterBatchParsingFixture.writeManifest;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.put;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockStatic;

class StockMasterBatchParsingServiceTest {
    @TempDir
    Path root;
    private final StockMasterBatchParsingService service = new StockMasterBatchParsingService(new KisStockMasterParser());

    @Test
    void parsesBothMarketsAndLinksExactCollectionMetadataWithoutChangingFiles() throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        var before = new HashMap<Path, byte[]>();
        try (var paths = Files.walk(batch)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                before.put(path, Files.readAllBytes(path));
            }
        }

        StockMasterBatchParseResult result = service.parseBatch(root, collection.collectionId());

        assertThat(result.collection()).isEqualTo(collection);
        assertThat(result.collection().evidenceScope()).isEqualTo("CURRENT_OBSERVATION");
        assertThat(result.marketResults()).extracting("market").containsExactly(KisStockMasterMarket.KOSPI, KisStockMasterMarket.KOSDAQ);
        assertThat(result.marketResults().getFirst().records().getFirst().symbol()).isEqualTo("005930");
        assertThat(result.marketResults().getLast().records().getFirst().symbol()).isEqualTo("0001A0");
        for (var parsed : result.marketResults()) {
            var file = collection.files().stream().filter(observation -> observation.market() == parsed.market()).findFirst().orElseThrow();
            assertThat(parsed.inputSha256()).isEqualTo(file.extractedSha256());
        }
        assertThat(before).hasSize(7);
        for (var file : before.entrySet()) {
            assertThat(Files.readAllBytes(file.getKey())).isEqualTo(file.getValue());
        }
    }

    @Test
    void preservesBlankAndUnknownClassificationFields() throws Exception {
        byte[] kospi = row(KisStockMasterMarket.KOSPI);
        put(kospi, 61, "EN");
        put(kospi, 83, "9");
        put(kospi, 219, "9");
        var collection = collect(root, content(kospi), content(row(KisStockMasterMarket.KOSDAQ, "0001A0", "KR70001A0001", "ALPHA")));
        var result = service.parseBatch(root, collection.collectionId());

        var first = result.marketResults().getFirst().records().getFirst();
        assertThat(first.rawGroup()).isEqualTo("EN");
        assertThat(first.rawEtp()).isEqualTo("9");
        assertThat(first.rawPreferred()).isEqualTo("9");
        assertThat(result.marketResults().getLast().records().getFirst().rawEtp()).isEqualTo(" ");
    }

    @Test
    void parsesMarketsInCanonicalOrderEvenIfManifestOrderDiffers() throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        var manifest = manifest(batch);
        var files = manifest.withArray("files");
        var first = files.get(0).deepCopy();
        files.set(0, files.get(1).deepCopy());
        files.set(1, first);
        writeManifest(batch, manifest);

        assertThat(service.parseBatch(root, collection.collectionId()).marketResults()).extracting("market")
                .containsExactly(KisStockMasterMarket.KOSPI, KisStockMasterMarket.KOSDAQ);
    }

    @Test
    void rejectsMissingRootBatchAndFileRootWithoutCreatingDirectories() throws Exception {
        Path missing = root.resolve("missing");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.parseBatch(missing, id)).isInstanceOf(IOException.class).hasMessageContaining("collectionId=" + id);
        assertThat(missing).doesNotExist();
        assertThatThrownBy(() -> service.parseBatch(root, id)).isInstanceOf(IOException.class);
        Path fileRoot = Files.writeString(root.resolve("file-root"), "not-a-directory");
        assertThatThrownBy(() -> service.parseBatch(fileRoot, id)).isInstanceOf(IOException.class)
                .hasMessageContaining("existing directory");
    }

    @Test
    void rejectsMissingManifestAndPartialOnlyManifest() throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        Files.move(batch.resolve("manifest.json"), batch.resolve("manifest.json.partial"));

        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class);
        assertThat(batch.resolve("manifest.json.partial")).exists();
        assertThat(batch.resolve("manifest.json")).doesNotExist();
    }

    @Test
    void rejectsManifestIdentityMismatch() throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        var manifest = manifest(batch);
        manifest.put("collectionId", UUID.randomUUID().toString());
        writeManifest(batch, manifest);

        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("collectionId=" + collection.collectionId(), "does not match the requested batch");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "not-json", "{\"collectionId\":1,\"collectionId\":2}"})
    void rejectsMalformedNullOrDuplicateJson(String json) throws Exception {
        var collection = collect(root);
        Files.writeString(batch(root, collection).resolve("manifest.json"), json);
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class);
    }

    @Test
    void rejectsTrailingJsonAndOversizedManifest() throws Exception {
        var collection = collect(root);
        Path path = batch(root, collection).resolve("manifest.json");
        String json = Files.readString(path);
        Files.writeString(path, json + " {}");
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class);
        Files.write(path, new byte[StockMasterBatchParsingService.MAX_JSON_BYTES + 1]);
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("limit=" + StockMasterBatchParsingService.MAX_JSON_BYTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"format", "scope", "partial", "duplicate", "timestamp", "unknown-property"})
    void rejectsUnsupportedOrIncompleteCollectionMetadata(String kind) throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        var manifest = manifest(batch);
        switch (kind) {
            case "format" -> manifest.put("formatVersion", 2);
            case "scope" -> manifest.put("evidenceScope", "AS_OF_VERIFIED");
            case "partial" -> manifest.withArray("files").remove(1);
            case "duplicate" -> manifest.withArray("files").set(1, manifest.withArray("files").get(0).deepCopy());
            case "timestamp" -> manifest.put("finishedAt", "2020-01-01T00:00:00Z");
            case "unknown-property" -> manifest.put("unexpected", true);
            default -> throw new IllegalArgumentException("Unknown fixture case.");
        }
        writeManifest(batch, manifest);
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"archivePath", "extractedPath", "sourceUri", "archiveSha256"})
    void rejectsInvalidSourceFileReferencesOrHashes(String field) throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        changeObservation(batch, KisStockMasterMarket.KOSPI, file -> file.put(field,
                field.equals("sourceUri") ? "https://example.invalid/other.zip" : "../../outside"));

        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "mismatch", "oversized"})
    void requiresPerMarketObservationToMatchManifest(String kind) throws Exception {
        var collection = collect(root);
        Path path = batch(root, collection).resolve("KOSDAQ/observation.json");
        switch (kind) {
            case "missing" -> Files.delete(path);
            case "mismatch" -> Files.writeString(path, "null");
            case "oversized" -> Files.write(path, new byte[StockMasterBatchParsingService.MAX_JSON_BYTES + 1]);
            default -> throw new IllegalArgumentException("Unknown fixture case.");
        }
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("market=KOSDAQ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"KOSPI/kospi_code.mst.zip", "KOSPI/kospi_code.mst", "KOSDAQ/kosdaq_code.mst.zip", "KOSDAQ/kosdaq_code.mst"})
    void rejectsMissingDataFiles(String relative) throws Exception {
        var collection = collect(root);
        Files.delete(batch(root, collection).resolve(relative));
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KOSPI/kospi_code.mst.zip", "KOSPI/kospi_code.mst"})
    void rejectsSameLengthFileTampering(String relative) throws Exception {
        var collection = collect(root);
        Path path = batch(root, collection).resolve(relative);
        byte[] bytes = Files.readAllBytes(path);
        bytes[0] ^= 1;
        Files.write(path, bytes);
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("market=KOSPI", "SHA-256");
    }

    @ParameterizedTest
    @ValueSource(strings = {"archiveBytes", "extractedBytes"})
    void rejectsRecordedSizeMismatchAndLimitsBeforeReading(String field) throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        changeObservation(batch, KisStockMasterMarket.KOSPI, file -> file.put(field, file.path(field).asLong() + 1));
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("File size does not match");
        long limit = field.equals("archiveBytes") ? KisStockMasterClient.MAX_ARCHIVE_BYTES : KisStockMasterParser.MAX_CONTENT_BYTES;
        changeObservation(batch, KisStockMasterMarket.KOSPI, file -> file.put(field, limit + 1));
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("Recorded file size", "within limit=" + limit);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void boundsActualReadAndRejectsSizeChangesAfterPreflight(int difference) throws Exception {
        var collection = collect(root);
        Path mst = batch(root, collection).resolve("KOSPI/kospi_code.mst").toRealPath();
        byte[] changed = Arrays.copyOf(Files.readAllBytes(mst), (int) Files.size(mst) + difference);
        try (MockedStatic<Files> ignored = mockStatic(Files.class, invocation -> {
            if (invocation.getMethod().getName().equals("newInputStream") && mst.equals(invocation.getArgument(0))) {
                return new ByteArrayInputStream(changed);
            }
            return invocation.callRealMethod();
        })) {
            assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                    .hasMessageContaining(difference > 0 ? "Actual file content exceeds limit" : "File size changed during the read");
        }
    }

    @Test
    void rejectsInvalidSecondMarketInsteadOfReturningTheFirstMarket() throws Exception {
        var collection = collect(root, content(row(KisStockMasterMarket.KOSPI)), "invalid\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("collectionId=" + collection.collectionId(), "market=KOSDAQ", "line=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"symbol", "standardCode"})
    void rejectsCrossMarketIdentifierCollisions(String field) throws Exception {
        byte[] kosdaq = row(KisStockMasterMarket.KOSDAQ,
                field.equals("symbol") ? "005930" : "0001A0",
                field.equals("standardCode") ? "KR7005930003" : "KR70001A0001", "COLLISION");
        var collection = collect(root, content(row(KisStockMasterMarket.KOSPI)), content(kosdaq));
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("Batch " + field + " collision", "market=KOSDAQ", "firstMarket=KOSPI", "line=1");
    }

    @Test
    void rejectsFileLinksAndBatchDirectoryLinksWhenSupported() throws Exception {
        var collection = collect(root);
        Path batch = batch(root, collection);
        Path target = batch.resolve("KOSPI/kospi_code.mst");
        Path original = batch.resolve("KOSPI/saved.mst");
        Files.move(target, original);
        try {
            Files.createSymbolicLink(target, original.toAbsolutePath());
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "This filesystem cannot create symbolic links: " + exception.getClass().getSimpleName());
        }
        assertThatThrownBy(() -> service.parseBatch(root, collection.collectionId())).isInstanceOf(IOException.class)
                .hasMessageContaining("without path redirection");
        UUID alias = UUID.randomUUID();
        Files.createSymbolicLink(root.resolve(alias.toString()), batch.toAbsolutePath());
        assertThatThrownBy(() -> service.parseBatch(root, alias)).isInstanceOf(IOException.class)
                .hasMessageContaining("Batch directory must not redirect");
    }

    @Test
    void rejectsNullDependenciesAndArguments() {
        assertThatThrownBy(() -> new StockMasterBatchParsingService(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.parseBatch(null, UUID.randomUUID())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.parseBatch(root, null)).isInstanceOf(NullPointerException.class);
    }
}
