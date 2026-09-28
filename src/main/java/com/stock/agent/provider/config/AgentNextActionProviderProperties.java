package com.stock.agent.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "agent.next-action")
public record AgentNextActionProviderProperties(
        AgentNextActionProviderType providerType
) {
    public AgentNextActionProviderProperties {
        Objects.requireNonNull(
                providerType,
                "Agent next action provider type must not be null."
        );
    }
}
