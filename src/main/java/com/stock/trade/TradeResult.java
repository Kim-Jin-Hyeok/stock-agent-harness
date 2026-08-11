package com.stock.trade;

import com.stock.agent.InvestmentAction;

public record TradeResult(
        TradeStatus status,
        InvestmentAction action,
        String symbol,
        Long orderAmountKrw,
        TradeReasonCode reasonCode,
        String reason
) {
}
