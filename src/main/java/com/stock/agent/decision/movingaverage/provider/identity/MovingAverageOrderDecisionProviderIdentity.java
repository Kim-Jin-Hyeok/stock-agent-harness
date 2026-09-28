package com.stock.agent.decision.movingaverage.provider.identity;

public record MovingAverageOrderDecisionProviderIdentity(
        String providerId,
        int providerVersion
) {
    public MovingAverageOrderDecisionProviderIdentity {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException(
                    "providerId must not be blank."
            );
        }
        if (providerVersion < 1) {
            throw new IllegalArgumentException(
                    "providerVersion must be at least 1."
            );
        }
    }
}
