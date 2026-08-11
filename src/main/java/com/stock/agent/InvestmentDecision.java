package com.stock.agent;

public record InvestmentDecision(
        InvestmentAction action,
        String symbol,
        String reason
) {
}
