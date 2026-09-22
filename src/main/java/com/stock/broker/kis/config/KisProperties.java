package com.stock.broker.kis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "broker.kis")
public record KisProperties(
        boolean enabled,
        URI baseUrl,
        String appKey,
        String appSecret,
        String accountNumber,
        String accountProductCode,
        Duration tokenRefreshBeforeExpiration
) {
    public KisProperties {
        if (tokenRefreshBeforeExpiration != null
                && tokenRefreshBeforeExpiration.isNegative()) {
            throw new IllegalArgumentException(
                    "tokenRefreshBeforeExpiration must not be negative."
            );
        }
        if (enabled) {
            Objects.requireNonNull(baseUrl, "baseUrl must not be null.");
            appKey = requireText(appKey, "appKey");
            appSecret = requireText(appSecret, "appSecret");
            accountNumber = requireText(accountNumber, "accountNumber");
            accountProductCode = requireText(
                    accountProductCode,
                    "accountProductCode"
            );
            Objects.requireNonNull(
                    tokenRefreshBeforeExpiration,
                    "tokenRefreshBeforeExpiration must not be null."
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }
}
