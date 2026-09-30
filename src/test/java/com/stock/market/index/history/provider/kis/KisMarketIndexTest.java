package com.stock.market.index.history.provider.kis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisMarketIndexTest {

    @Test
    void mapsKospiBenchmarkIdToKisInputCode() {
        KisMarketIndex marketIndex = KisMarketIndex.fromBenchmarkId(
                "KOSPI"
        );

        assertThat(marketIndex).isEqualTo(KisMarketIndex.KOSPI);
        assertThat(marketIndex.inputCode()).isEqualTo("0001");
    }

    @Test
    void rejectsUnsupportedBenchmarkId() {
        assertThatThrownBy(() -> KisMarketIndex.fromBenchmarkId("KOSDAQ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported KIS market index: KOSDAQ");
    }
}
