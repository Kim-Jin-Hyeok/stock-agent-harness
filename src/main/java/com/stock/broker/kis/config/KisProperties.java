package com.stock.broker.kis.config;

import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryMarket;
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
        Duration tokenRefreshBeforeExpiration,
        int accountBalanceMaxPages,
        int orderInquiryMaxPages,
        int cancelableOrderInquiryMaxPages,
        int dailyPriceHistoryMaxPages,
        Duration dailyPriceHistoryRequestDelay,
        KisDailyPriceHistoryMarket dailyPriceHistoryMarket
) {
    public KisProperties {
        if (tokenRefreshBeforeExpiration != null
                && tokenRefreshBeforeExpiration.isNegative()) {
            throw new IllegalArgumentException(
                    "tokenRefreshBeforeExpiration must not be negative."
            );
        }
        if (accountBalanceMaxPages <= 0) {
            throw new IllegalArgumentException(
                    "accountBalanceMaxPages must be positive."
            );
        }
        if (orderInquiryMaxPages <= 0) {
            throw new IllegalArgumentException(
                    "orderInquiryMaxPages must be positive."
            );
        }
        if (cancelableOrderInquiryMaxPages <= 0) {
            throw new IllegalArgumentException(
                    "cancelableOrderInquiryMaxPages must be positive."
            );
        }
        if (dailyPriceHistoryMaxPages <= 0) {
            throw new IllegalArgumentException(
                    "dailyPriceHistoryMaxPages must be positive."
            );
        }
        if (dailyPriceHistoryRequestDelay == null
                || dailyPriceHistoryRequestDelay.isNegative()) {
            throw new IllegalArgumentException(
                    "dailyPriceHistoryRequestDelay must not be null or negative."
            );
        }
        Objects.requireNonNull(
                dailyPriceHistoryMarket,
                "dailyPriceHistoryMarket must not be null."
        );
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
