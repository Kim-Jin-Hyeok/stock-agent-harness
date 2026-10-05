package com.stock.market.stock.master.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult.FileObservation;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class StockMasterCollectionService {
    public static final long MAX_EXTRACTED_BYTES = 256L * 1024 * 1024;
    private static final ObjectWriter OBSERVATION_WRITER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writerWithDefaultPrettyPrinter();

    private final KisStockMasterClient client;
    private final Clock clock;
    private final long maxExtractedBytes;

    public StockMasterCollectionService(KisStockMasterClient client, Clock clock, long maxExtractedBytes) {
        this.client = Objects.requireNonNull(client, "client must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        if (maxExtractedBytes <= 0 || maxExtractedBytes > MAX_EXTRACTED_BYTES) {
            throw new IllegalArgumentException("maxExtractedBytes must be positive and at most 256 MiB.");
        }
        this.maxExtractedBytes = maxExtractedBytes;
    }

    public StockMasterCollectionResult collect(Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null.");
        Path root = Files.createDirectories(outputDirectory.toAbsolutePath().normalize()).toRealPath();
        UUID collectionId = UUID.randomUUID();
        Path batch = Files.createDirectory(root.resolve(collectionId.toString()));
        Instant startedAt = clock.instant();
        var observations = new ArrayList<FileObservation>();
        for (KisStockMasterMarket market : KisStockMasterMarket.values()) {
            try {
                observations.add(collectMarket(batch, market));
            } catch (IOException | RuntimeException e) {
                // Keep partial evidence, but never publish a complete manifest after a failure.
                throw new IOException("Stock master collection failed. collectionId=" + collectionId
                        + ", market=" + market + ", directory=" + batch, e);
            }
        }
        var result = new StockMasterCollectionResult(
                1, collectionId, "CURRENT_OBSERVATION", startedAt, clock.instant(), observations
        );
        writeObservation(batch.resolve("manifest.json"), result);
        return result;
    }

    private FileObservation collectMarket(Path batch, KisStockMasterMarket market) throws IOException {
        Instant startedAt = clock.instant();
        Path directory = Files.createDirectory(batch.resolve(market.name()));
        byte[] archive = client.download(market);
        Path archivePath = directory.resolve(market.fileName() + ".zip");
        Files.write(archivePath, archive, StandardOpenOption.CREATE_NEW);
        Path extractedPath = directory.resolve(market.fileName());
        String extractedSha256 = extract(archivePath, extractedPath, market.fileName());
        var observation = new FileObservation(market, market.sourceUri(), startedAt, clock.instant(),
                market.name() + "/" + archivePath.getFileName(), archive.length, sha256(archive),
                market.name() + "/" + extractedPath.getFileName(), Files.size(extractedPath), extractedSha256);
        writeObservation(directory.resolve("observation.json"), observation);
        return observation;
    }

    private static void writeObservation(Path destination, Object value) throws IOException {
        Path partial = destination.resolveSibling(destination.getFileName() + ".partial");
        Files.write(partial, OBSERVATION_WRITER.writeValueAsBytes(value), StandardOpenOption.CREATE_NEW);
        Files.move(partial, destination, StandardCopyOption.ATOMIC_MOVE);
    }

    private String extract(Path archivePath, Path extractedPath, String expectedName) throws IOException {
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            if (zip.size() != 1) {
                throw new IOException("Stock master ZIP must contain exactly one expected file.");
            }
            ZipEntry entry = zip.entries().nextElement();
            if (entry.isDirectory() || !expectedName.equals(entry.getName())) {
                throw new IOException("Unexpected stock master ZIP entry.");
            }
            if (entry.getSize() <= 0 || entry.getSize() > maxExtractedBytes) {
                throw new IOException("Stock master extracted size must be positive and within maxExtractedBytes.");
            }
            Path partial = extractedPath.resolveSibling(expectedName + ".partial");
            MessageDigest digest = newDigest();
            CRC32 crc = new CRC32();
            long total = 0;
            try (InputStream input = zip.getInputStream(entry);
                 var output = Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (count > maxExtractedBytes - total) {
                        throw new IOException("Stock master extracted content exceeds maxExtractedBytes.");
                    }
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    crc.update(buffer, 0, count);
                    total += count;
                }
            }
            if (total != entry.getSize() || crc.getValue() != entry.getCrc()) {
                throw new IOException("Stock master ZIP entry size or CRC does not match extracted content.");
            }
            Files.move(partial, extractedPath, StandardCopyOption.ATOMIC_MOVE);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available.", e);
        }
    }
}
