package com.stock.risk;

public record RiskCheckResult(
        RiskCheckStatus status,
        String reason
) {
}
