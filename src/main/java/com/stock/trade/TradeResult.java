package com.stock.trade;

public record TradeResult(
        TradeStatus status,
        String reason
) {
}
