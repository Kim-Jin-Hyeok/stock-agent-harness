package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolTypeTest {

    @Test
    void containsOnlyReadToolsAtThisStage() {
        assertThat(HarnessToolType.values())
                .containsExactly(
                        HarnessToolType.GET_PORTFOLIO,
                        HarnessToolType.GET_MARKET
                );
    }
}
