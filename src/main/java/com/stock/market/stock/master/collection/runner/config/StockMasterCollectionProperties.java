package com.stock.market.stock.master.collection.runner.config;

import com.stock.market.stock.master.collection.StockMasterCollectionService;
import com.stock.market.stock.master.provider.kis.KisStockMasterClient;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "market.stock.master.collection.manual")
public record StockMasterCollectionProperties(
        boolean enabled,
        String outputDirectory,
        Duration connectTimeout,
        Duration downloadTimeout,
        Integer maxArchiveBytes,
        Long maxExtractedBytes
) {
    public StockMasterCollectionProperties {
        if (enabled) {
            if (outputDirectory == null || outputDirectory.isBlank()) {
                throw new IllegalArgumentException("outputDirectory must not be blank.");
            }
            Path.of(outputDirectory);
            Objects.requireNonNull(connectTimeout, "connectTimeout must not be null.");
            Objects.requireNonNull(downloadTimeout, "downloadTimeout must not be null.");
            Objects.requireNonNull(maxArchiveBytes, "maxArchiveBytes must not be null.");
            Objects.requireNonNull(maxExtractedBytes, "maxExtractedBytes must not be null.");
            if (connectTimeout.isZero() || connectTimeout.isNegative()
                    || downloadTimeout.isZero() || downloadTimeout.isNegative()
                    || connectTimeout.compareTo(downloadTimeout) > 0
                    || downloadTimeout.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException("Timeouts must be positive, connect <= download, download <= 5 minutes.");
            }
            if (maxArchiveBytes <= 0 || maxArchiveBytes > KisStockMasterClient.MAX_ARCHIVE_BYTES) {
                throw new IllegalArgumentException("maxArchiveBytes must be positive and at most 64 MiB.");
            }
            if (maxExtractedBytes <= 0 || maxExtractedBytes > StockMasterCollectionService.MAX_EXTRACTED_BYTES) {
                throw new IllegalArgumentException("maxExtractedBytes must be positive and at most 256 MiB.");
            }
        }
    }
}
