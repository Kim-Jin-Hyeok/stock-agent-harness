package com.stock.market.stock.master.parsing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult.FileObservation;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.result.KisStockMasterParseResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public class StockMasterBatchParsingService {
    public static final int MAX_JSON_BYTES = 64 * 1024;
    private static final ObjectReader JSON_READER = new ObjectMapper().registerModule(new JavaTimeModule())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).reader();
    private final KisStockMasterParser parser;

    public StockMasterBatchParsingService(KisStockMasterParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null.");
    }

    public StockMasterBatchParseResult parseBatch(Path observationRoot, UUID collectionId) throws IOException {
        Objects.requireNonNull(observationRoot, "observationRoot must not be null.");
        Objects.requireNonNull(collectionId, "collectionId must not be null.");
        try {
            Path root = observationRoot.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IOException("Observation root must be an existing directory.");
            }
            Path requestedBatch = root.resolve(collectionId.toString());
            Path batch = requestedBatch.toRealPath();
            if (!batch.equals(requestedBatch) || !Files.isDirectory(batch, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Batch directory must not redirect to another path.");
            }
            var collection = readJson(safeFile(batch, "manifest.json"), StockMasterCollectionResult.class);
            if (collection == null || !collectionId.equals(collection.collectionId())) {
                throw new IOException("Manifest collectionId does not match the requested batch.");
            }
            var results = new ArrayList<KisStockMasterParseResult>();
            for (var market : KisStockMasterMarket.values()) {
                var file = collection.files().stream().filter(observation -> observation.market() == market)
                        .findFirst().orElseThrow();
                try {
                    var observation = readJson(safeFile(batch, market.name() + "/observation.json"), FileObservation.class);
                    if (!file.equals(observation)) {
                        throw new IOException("Per-market observation does not match the manifest.");
                    }
                    readVerifiedFile(safeFile(batch, file.archivePath()), file.archiveBytes(), file.archiveSha256(),
                            KisStockMasterClient.MAX_ARCHIVE_BYTES, false);
                    byte[] content = readVerifiedFile(safeFile(batch, file.extractedPath()), file.extractedBytes(),
                            file.extractedSha256(), KisStockMasterParser.MAX_CONTENT_BYTES, true);
                    results.add(parser.parse(market, content));
                } catch (IOException | RuntimeException exception) {
                    throw new IOException("Market validation failed. market=" + market + ", cause=" + exception.getMessage(), exception);
                }
            }
            return new StockMasterBatchParseResult(collection, results);
        } catch (IOException | RuntimeException exception) {
            throw new IOException("Stock master batch parsing failed. collectionId=" + collectionId
                    + ", cause=" + exception.getMessage(), exception);
        }
    }

    private static Path safeFile(Path batch, String relativePath) throws IOException {
        Path candidate = batch.resolve(relativePath).normalize();
        if (!candidate.startsWith(batch)) {
            throw new IOException("File reference escapes the selected batch.");
        }
        Path real = candidate.toRealPath();
        if (!real.equals(candidate) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Batch file must be a regular file without path redirection. path=" + relativePath);
        }
        return real;
    }

    private static <T> T readJson(Path path, Class<T> type) throws IOException {
        byte[] content = readFile(path, MAX_JSON_BYTES, true).content();
        return JSON_READER.forType(type).readValue(content);
    }

    private static byte[] readVerifiedFile(
            Path path, long expectedBytes, String expectedSha256, int limit, boolean retainContent
    ) throws IOException {
        if (expectedBytes <= 0 || expectedBytes > limit) {
            throw new IOException("Recorded file size must be positive and within limit=" + limit + ". path=" + path.getFileName());
        }
        if (Files.size(path) != expectedBytes) {
            throw new IOException("File size does not match the manifest. path=" + path.getFileName());
        }
        var file = readFile(path, (int) expectedBytes, retainContent);
        if (file.bytes() != expectedBytes || !file.sha256().equals(expectedSha256)) {
            throw new IOException("File bytes or SHA-256 do not match the manifest. path=" + path.getFileName());
        }
        return file.content();
    }

    private static ReadFileResult readFile(Path path, int limit, boolean retainContent) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > limit) {
            throw new IOException("File must be nonempty and within limit=" + limit + ". path=" + path.getFileName());
        }
        MessageDigest digest = newDigest();
        var output = retainContent ? new ByteArrayOutputStream() : null;
        long total = 0;
        // Only retain JSON/MST bytes; archive verification is a bounded streaming hash.
        try (var input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > limit - total) {
                    throw new IOException("Actual file content exceeds limit=" + limit + ". path=" + path.getFileName());
                }
                digest.update(buffer, 0, count);
                if (output != null) {
                    output.write(buffer, 0, count);
                }
                total += count;
            }
        }
        if (total != size) {
            throw new IOException("File size changed during the read. path=" + path.getFileName());
        }
        return new ReadFileResult(total, HexFormat.of().formatHex(digest.digest()), output == null ? null : output.toByteArray());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }

    private record ReadFileResult(long bytes, String sha256, byte[] content) {
    }
}
