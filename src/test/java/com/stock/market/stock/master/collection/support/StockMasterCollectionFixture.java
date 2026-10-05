package com.stock.market.stock.master.collection.support;

import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult.FileObservation;
import com.stock.market.stock.master.collection.runner.config.StockMasterCollectionProperties;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class StockMasterCollectionFixture {
    public static final Instant NOW = Instant.parse("2026-10-05T01:02:03.123456789Z");
    public static final byte[] CONTENT = "005930    ORIGINAL\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private StockMasterCollectionFixture() {
    }

    public static StockMasterCollectionProperties properties(Path directory) {
        return new StockMasterCollectionProperties(true, directory.toString(), Duration.ofSeconds(1),
                Duration.ofSeconds(5), 1024, 2048L);
    }

    public static byte[] zip(String... entryNames) throws IOException {
        return zip(CONTENT, entryNames);
    }

    public static byte[] zip(byte[] content, String... entryNames) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : entryNames) {
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static StockMasterCollectionResult result() {
        List<FileObservation> files = Arrays.stream(KisStockMasterMarket.values()).map(market -> new FileObservation(
                market, market.sourceUri(), NOW, NOW, market.name() + "/" + market.fileName() + ".zip", 100,
                "a".repeat(64), market.name() + "/" + market.fileName(), CONTENT.length, "b".repeat(64)
        )).toList();
        return new StockMasterCollectionResult(1, UUID.fromString("ccfc88a2-f635-4f34-891f-ec9a416dc61f"),
                "CURRENT_OBSERVATION", NOW, NOW, files);
    }
}
