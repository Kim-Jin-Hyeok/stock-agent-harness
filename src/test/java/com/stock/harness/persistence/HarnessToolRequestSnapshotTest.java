package com.stock.harness.persistence;

import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolRequestSnapshotTest {

    @Test
    void fromCopiesToolRequest() {
        HarnessToolRequestSnapshot snapshot = HarnessToolRequestSnapshot.from(
                HarnessToolRequest.currentPrice("005930")
        );

        assertThat(snapshot.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(snapshot.symbol()).isEqualTo("005930");
    }
}
