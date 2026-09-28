package com.stock.agent.decision.movingaverage.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "agent.decision.moving-average")
public record MovingAverageOrderDecisionProperties(
        MovingAverageOrderDecisionProviderType providerType
) {
    public MovingAverageOrderDecisionProperties {
        Objects.requireNonNull(
                providerType,
                "Moving average order decision provider type must not be null."
        );
    }
}
