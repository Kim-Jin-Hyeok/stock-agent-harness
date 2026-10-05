package com.stock.market.stock.master.parsing.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.collection.StockMasterCollectionService;
import com.stock.market.stock.master.collection.result.StockMasterCollectionResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.function.Consumer;

import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.NOW;
import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.zip;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.row;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class StockMasterBatchParsingFixture {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private StockMasterBatchParsingFixture() {
    }

    public static StockMasterCollectionResult collect(Path root) throws IOException {
        return collect(root, content(row(KisStockMasterMarket.KOSPI)),
                content(row(KisStockMasterMarket.KOSDAQ, "0001A0", "KR70001A0001", "ALPHA")));
    }

    public static StockMasterCollectionResult collect(Path root, byte[] kospi, byte[] kosdaq) throws IOException {
        var client = mock(KisStockMasterClient.class);
        when(client.download(KisStockMasterMarket.KOSPI)).thenReturn(zip(kospi, KisStockMasterMarket.KOSPI.fileName()));
        when(client.download(KisStockMasterMarket.KOSDAQ)).thenReturn(zip(kosdaq, KisStockMasterMarket.KOSDAQ.fileName()));
        return new StockMasterCollectionService(client, Clock.fixed(NOW, ZoneOffset.UTC), KisStockMasterParser.MAX_CONTENT_BYTES)
                .collect(root);
    }

    public static Path batch(Path root, StockMasterCollectionResult collection) {
        return root.resolve(collection.collectionId().toString());
    }

    public static ObjectNode manifest(Path batch) throws IOException {
        return (ObjectNode) JSON.readTree(batch.resolve("manifest.json").toFile());
    }

    public static void writeManifest(Path batch, ObjectNode manifest) throws IOException {
        JSON.writeValue(batch.resolve("manifest.json").toFile(), manifest);
    }

    public static void changeObservation(Path batch, KisStockMasterMarket market, Consumer<ObjectNode> change) throws IOException {
        ObjectNode manifest = manifest(batch);
        for (var file : manifest.withArray("files")) {
            if (file.path("market").asText().equals(market.name())) {
                change.accept((ObjectNode) file);
                JSON.writeValue(batch.resolve(market.name()).resolve("observation.json").toFile(), file);
                writeManifest(batch, manifest);
                return;
            }
        }
        throw new IllegalArgumentException("Fixture market is missing.");
    }
}
