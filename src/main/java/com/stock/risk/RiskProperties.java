package com.stock.risk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "risk")
public record RiskProperties(
        double maxOrderRatio,
        double maxPositionRatio
) {
}
