package com.stock.harness.persistence;

import com.stock.market.price.CurrentPriceSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessCurrentPriceSnapshotTest {

    @Test
    void fromCopiesCurrentPriceValues() {
        HarnessCurrentPriceSnapshot snapshot = HarnessCurrentPriceSnapshot.from(
                new CurrentPriceSnapshot("005930", 70_000L)
        );

        assertThat(snapshot.symbol()).isEqualTo("005930");
        assertThat(snapshot.priceKrw()).isEqualTo(70_000L);
    }
}
