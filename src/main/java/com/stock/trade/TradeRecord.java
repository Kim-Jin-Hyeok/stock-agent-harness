package com.stock.trade;

import com.stock.agent.InvestmentAction;

import java.time.LocalDateTime;

public record TradeRecord(
        String runId,
        InvestmentAction action,
        String symbol,
        Long quantity,
        Long priceKrw,
        Long orderAmountKrw,
        TradeStatus status,
        TradeReasonCode reasonCode,
        String reason,
        LocalDateTime executedAt
) {
}
