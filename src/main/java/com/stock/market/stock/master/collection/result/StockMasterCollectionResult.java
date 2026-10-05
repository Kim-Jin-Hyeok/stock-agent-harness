package com.stock.market.stock.master.collection.result;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StockMasterCollectionResult(
        int formatVersion,
        UUID collectionId,
        String evidenceScope,
        Instant startedAt,
        Instant finishedAt,
        List<FileObservation> files
) {
    public StockMasterCollectionResult {
        if (formatVersion != 1 || !"CURRENT_OBSERVATION".equals(evidenceScope)) {
            throw new IllegalArgumentException("Unsupported stock master observation format or scope.");
        }
        Objects.requireNonNull(collectionId, "collectionId must not be null.");
        Objects.requireNonNull(startedAt, "startedAt must not be null.");
        Objects.requireNonNull(finishedAt, "finishedAt must not be null.");
        files = List.copyOf(files);
        if (finishedAt.isBefore(startedAt) || files.size() != 2
                || files.stream().map(FileObservation::market).distinct().count() != 2
                || files.stream().anyMatch(file -> file.startedAt().isBefore(startedAt)
                || file.finishedAt().isAfter(finishedAt))) {
            throw new IllegalArgumentException("Collection must contain both markets with consistent timestamps.");
        }
    }

    public record FileObservation(
            KisStockMasterMarket market,
            URI sourceUri,
            Instant startedAt,
            Instant finishedAt,
            String archivePath,
            long archiveBytes,
            String archiveSha256,
            String extractedPath,
            long extractedBytes,
            String extractedSha256
    ) {
        public FileObservation {
            Objects.requireNonNull(market, "market must not be null.");
            Objects.requireNonNull(startedAt, "startedAt must not be null.");
            Objects.requireNonNull(finishedAt, "finishedAt must not be null.");
            if (!market.sourceUri().equals(sourceUri) || finishedAt.isBefore(startedAt)
                    || archiveBytes <= 0 || extractedBytes <= 0
                    || !isSha256(archiveSha256) || !isSha256(extractedSha256)
                    || !(market.name() + "/" + market.fileName() + ".zip").equals(archivePath)
                    || !(market.name() + "/" + market.fileName()).equals(extractedPath)) {
                throw new IllegalArgumentException("Invalid stock master file observation.");
            }
        }

        private static boolean isSha256(String value) {
            return value != null && value.matches("[0-9a-f]{64}");
        }
    }
}
