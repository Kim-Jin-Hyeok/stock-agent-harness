package com.stock.market.stock.master.collection.result;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.stock.market.stock.master.collection.support.StockMasterCollectionFixture.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMasterCollectionResultTest {
    @Test
    void defensivelyCopiesFileObservations() {
        var original = result();
        var files = new ArrayList<>(original.files());
        var copy = new StockMasterCollectionResult(1, original.collectionId(), original.evidenceScope(),
                original.startedAt(), original.finishedAt(), files);
        files.clear();

        assertThat(copy.files()).hasSize(2);
        assertThatThrownBy(() -> copy.files().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsPartialOrDuplicateMarketsAndVerifiedEvidenceScope() {
        var original = result();
        assertThatThrownBy(() -> new StockMasterCollectionResult(1, original.collectionId(), original.evidenceScope(),
                original.startedAt(), original.finishedAt(), List.of(original.files().getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMasterCollectionResult(1, original.collectionId(), original.evidenceScope(),
                original.startedAt(), original.finishedAt(), List.of(original.files().getFirst(), original.files().getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMasterCollectionResult(1, original.collectionId(), "AS_OF_VERIFIED",
                original.startedAt(), original.finishedAt(), original.files()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidHashAndUnboundedFileReference() {
        var file = result().files().getFirst();
        assertThatThrownBy(() -> new StockMasterCollectionResult.FileObservation(file.market(), file.sourceUri(),
                file.startedAt(), file.finishedAt(), "../archive.zip", file.archiveBytes(), file.archiveSha256(),
                file.extractedPath(), file.extractedBytes(), file.extractedSha256()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMasterCollectionResult.FileObservation(file.market(), file.sourceUri(),
                file.startedAt(), file.finishedAt(), file.archivePath(), file.archiveBytes(), "not-a-hash",
                file.extractedPath(), file.extractedBytes(), file.extractedSha256()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCollectionTimesThatDoNotContainMarketObservations() {
        var original = result();
        assertThatThrownBy(() -> new StockMasterCollectionResult(1, original.collectionId(), original.evidenceScope(),
                original.startedAt().plusNanos(1), original.finishedAt().plusNanos(2), original.files()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockMasterCollectionResult(1, original.collectionId(), original.evidenceScope(),
                original.startedAt().minusNanos(2), original.finishedAt().minusNanos(1), original.files()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
