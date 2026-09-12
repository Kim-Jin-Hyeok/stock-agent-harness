package com.stock.harness.tool;

public record HarnessToolRequest(
        HarnessToolType type,
        String symbol
) {
    public HarnessToolRequest(HarnessToolType type) {
        this(type, null);
    }

    public static HarnessToolRequest portfolio() {
        return new HarnessToolRequest(HarnessToolType.GET_PORTFOLIO);
    }

    public static HarnessToolRequest market() {
        return new HarnessToolRequest(HarnessToolType.GET_MARKET);
    }

    public static HarnessToolRequest currentPrice(String symbol) {
        return new HarnessToolRequest(HarnessToolType.GET_CURRENT_PRICE, symbol);
    }
}
