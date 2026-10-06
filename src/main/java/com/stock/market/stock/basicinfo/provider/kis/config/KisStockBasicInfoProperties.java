package com.stock.market.stock.basicinfo.provider.kis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "market.stock.basic-info.kis")
public record KisStockBasicInfoProperties(
        boolean enabled,
        String appKey,
        String appSecret,
        @DefaultValue("1m") Duration tokenRefreshBeforeExpiration,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration requestTimeout
) {
    public KisStockBasicInfoProperties {
        if (enabled) {
            requireCredential(appKey, "appKey");
            requireCredential(appSecret, "appSecret");
            if (tokenRefreshBeforeExpiration == null || tokenRefreshBeforeExpiration.isNegative()) {
                throw new IllegalArgumentException("tokenRefreshBeforeExpiration must not be null or negative.");
            }
            requirePositive(connectTimeout, "connectTimeout");
            requirePositive(requestTimeout, "requestTimeout");
            if (connectTimeout.compareTo(requestTimeout) > 0) {
                throw new IllegalArgumentException("connectTimeout must not exceed requestTimeout.");
            }
        }
    }

    @Override
    public String toString() {
        return "KisStockBasicInfoProperties[enabled=" + enabled + ", appKey=<redacted>, appSecret=<redacted>, "
                + "tokenRefreshBeforeExpiration=" + tokenRefreshBeforeExpiration + ", connectTimeout=" + connectTimeout
                + ", requestTimeout=" + requestTimeout + "]";
    }

    private static void requireCredential(String value, String name) {
        if (value == null || value.isBlank() || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException(name + " must be nonblank visible ASCII without whitespace.");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException(name + " must be at least 1 millisecond.");
        }
    }
}
