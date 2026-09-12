package com.stock.harness.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessToolRequestTest {

    @Test
    void keepsRequestedPortfolioToolType() {
        HarnessToolRequest request = getPortfolioRequest();

        assertThat(request.type()).isEqualTo(getPortfolioTool());
    }

    @Test
    void keepsRequestedMarketToolType() {
        HarnessToolRequest request = getMarketRequest();

        assertThat(request.type()).isEqualTo(getMarketTool());
    }

    @Test
    void createsCurrentPriceRequestWithSymbol() {
        HarnessToolRequest request = HarnessToolRequest.currentPrice("005930");

        assertThat(request.type()).isEqualTo(HarnessToolType.GET_CURRENT_PRICE);
        assertThat(request.symbol()).isEqualTo("005930");
    }

    private HarnessToolRequest getPortfolioRequest() {
        return new HarnessToolRequest(getPortfolioTool());
    }

    private HarnessToolRequest getMarketRequest() {
        return new HarnessToolRequest(getMarketTool());
    }

    private HarnessToolType getPortfolioTool() {
        return HarnessToolType.GET_PORTFOLIO;
    }

    private HarnessToolType getMarketTool() {
        return HarnessToolType.GET_MARKET;
    }
}
