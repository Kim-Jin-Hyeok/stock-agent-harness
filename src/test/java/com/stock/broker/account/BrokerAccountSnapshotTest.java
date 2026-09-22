package com.stock.broker.account;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerAccountSnapshotTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    void copiesPositionsAsImmutableList() {
        List<BrokerAccountPosition> source = new ArrayList<>(List.of(position()));

        BrokerAccountSnapshot snapshot = new BrokerAccountSnapshot(
                9_300_000L,
                10_000_000L,
                source,
                OBSERVED_AT
        );
        source.clear();

        assertThat(snapshot.positions()).containsExactly(position());
        assertThatThrownBy(() -> snapshot.positions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> new BrokerAccountSnapshot(
                -1L,
                0L,
                List.of(),
                OBSERVED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("depositAmountKrw must not be negative.");

        assertThatThrownBy(() -> new BrokerAccountSnapshot(
                0L,
                -1L,
                List.of(),
                OBSERVED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalAssetAmountKrw must not be negative.");
    }

    @Test
    void rejectsNullPositions() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerAccountSnapshot(
                        0L,
                        0L,
                        null,
                        OBSERVED_AT
                ))
                .withMessage("positions must not be null.");
    }

    @Test
    void rejectsNullObservedAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BrokerAccountSnapshot(
                        0L,
                        0L,
                        List.of(),
                        null
                ))
                .withMessage("observedAt must not be null.");
    }

    private BrokerAccountPosition position() {
        return new BrokerAccountPosition(
                "005930",
                10L,
                70_000L,
                700_000L
        );
    }
}
