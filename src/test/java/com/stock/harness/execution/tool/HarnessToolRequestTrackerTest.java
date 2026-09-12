package com.stock.harness.execution.tool;

import com.stock.harness.tool.HarnessToolRequest;
import com.stock.harness.tool.HarnessToolType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolRequestTrackerTest {

    @Test
    void rejectsSameRequestAfterFirstRegistration() {
        HarnessToolRequestTracker tracker = new HarnessToolRequestTracker();
        HarnessToolRequest request = request(HarnessToolType.GET_PORTFOLIO);

        assertThat(tracker.tryRegister(request)).isTrue();
        assertThat(tracker.tryRegister(request)).isFalse();
    }

    @Test
    void allowsDifferentRequests() {
        HarnessToolRequestTracker tracker = new HarnessToolRequestTracker();

        assertThat(tracker.tryRegister(request(HarnessToolType.GET_PORTFOLIO))).isTrue();
        assertThat(tracker.tryRegister(request(HarnessToolType.GET_MARKET))).isTrue();
    }

    @Test
    void allowsCurrentPriceRequestsForDifferentSymbols() {
        HarnessToolRequestTracker tracker = new HarnessToolRequestTracker();

        assertThat(tracker.tryRegister(HarnessToolRequest.currentPrice("005930"))).isTrue();
        assertThat(tracker.tryRegister(HarnessToolRequest.currentPrice("000660"))).isTrue();
    }

    private HarnessToolRequest request(HarnessToolType type) {
        return new HarnessToolRequest(type);
    }
}
