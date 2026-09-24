package com.stock.broker.kis.config;

import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryMarket;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KisPropertiesTest {
    private static final URI BASE_URL = URI.create(
            "https://openapivts.koreainvestment.com:29443"
    );

    @Test
    void allowsMissingCredentialsWhenDisabled() {
        KisProperties properties = new KisProperties(
                false,
                BASE_URL,
                "",
                "",
                "",
                "",
                Duration.ofMinutes(1),
                10,
                10,
                10,
                10,
                KisDailyPriceHistoryMarket.INTEGRATED
        );

        assertThat(properties.enabled()).isFalse();
    }

    @Test
    void rejectsBlankAppKeyWhenEnabled() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties(" ", "12345678"))
                .withMessage("appKey must not be blank.");
    }

    @Test
    void rejectsBlankAccountNumberWhenEnabled() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties("app-key", " "))
                .withMessage("accountNumber must not be blank.");
    }

    @Test
    void rejectsNegativeTokenRefreshBeforeExpiration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisProperties(
                        false,
                        BASE_URL,
                        "",
                        "",
                        "",
                        "",
                        Duration.ofSeconds(-1),
                        10,
                        10,
                        10,
                        10,
                        KisDailyPriceHistoryMarket.INTEGRATED
                ))
                .withMessage(
                        "tokenRefreshBeforeExpiration must not be negative."
                );
    }

    @Test
    void rejectsNonPositiveAccountBalanceMaxPages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisProperties(
                        false,
                        BASE_URL,
                        "",
                        "",
                        "",
                        "",
                        Duration.ofMinutes(1),
                        0,
                        10,
                        10,
                        10,
                        KisDailyPriceHistoryMarket.INTEGRATED
                ))
                .withMessage("accountBalanceMaxPages must be positive.");
    }

    @Test
    void rejectsNonPositiveOrderInquiryMaxPages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisProperties(
                        false,
                        BASE_URL,
                        "",
                        "",
                        "",
                        "",
                        Duration.ofMinutes(1),
                        10,
                        0,
                        10,
                        10,
                        KisDailyPriceHistoryMarket.INTEGRATED
                ))
                .withMessage("orderInquiryMaxPages must be positive.");
    }

    @Test
    void rejectsNonPositiveCancelableOrderInquiryMaxPages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisProperties(
                        false,
                        BASE_URL,
                        "",
                        "",
                        "",
                        "",
                        Duration.ofMinutes(1),
                        10,
                        10,
                        0,
                        10,
                        KisDailyPriceHistoryMarket.INTEGRATED
                ))
                .withMessage(
                        "cancelableOrderInquiryMaxPages must be positive."
                );
    }

    @Test
    void rejectsNonPositiveDailyPriceHistoryMaxPages() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new KisProperties(
                        false,
                        BASE_URL,
                        "",
                        "",
                        "",
                        "",
                        Duration.ofMinutes(1),
                        10,
                        10,
                        10,
                        0,
                        KisDailyPriceHistoryMarket.INTEGRATED
                ))
                .withMessage(
                        "dailyPriceHistoryMaxPages must be positive."
                );
    }

    private KisProperties properties(String appKey, String accountNumber) {
        return new KisProperties(
                true,
                BASE_URL,
                appKey,
                "app-secret",
                accountNumber,
                "01",
                Duration.ofMinutes(1),
                10,
                10,
                10,
                10,
                KisDailyPriceHistoryMarket.INTEGRATED
        );
    }
}
