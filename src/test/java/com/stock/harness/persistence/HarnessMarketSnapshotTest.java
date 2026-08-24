package com.stock.harness.persistence;

import com.stock.market.MarketSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessMarketSnapshotTest {

    @Test
    void fromCreatesOpenMarketSnapshot() {
        HarnessMarketSnapshot snapshot = HarnessMarketSnapshot.from(openMarketSnapshot());

        assertThat(snapshot.market()).isEqualTo("KR");
        assertThat(snapshot.marketOpen()).isTrue();
        assertThat(snapshot.description()).isEqualTo("Korean market is open.");
    }

    @Test
    void fromCreatesClosedMarketSnapshot() {
        HarnessMarketSnapshot snapshot = HarnessMarketSnapshot.from(closedMarketSnapshot());

        assertThat(snapshot.market()).isEqualTo("KR");
        assertThat(snapshot.marketOpen()).isFalse();
        assertThat(snapshot.description()).isEqualTo("Korean market is closed.");
    }

    private MarketSnapshot openMarketSnapshot() {
        return new MarketSnapshot(
                "KR",
                true,
                "Korean market is open."
        );
    }

    private MarketSnapshot closedMarketSnapshot() {
        return new MarketSnapshot(
                "KR",
                false,
                "Korean market is closed."
        );
    }
}
