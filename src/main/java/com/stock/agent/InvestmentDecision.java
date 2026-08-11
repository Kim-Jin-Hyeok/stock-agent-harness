package com.stock.agent;

public record InvestmentDecision(
        InvestmentAction action,
        String symbol,
        Long orderAmount,
        String reason
) {
}
