package com.stock.market.stock.basicinfo.provider.kis.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoPropertiesTest {
    private static final String APP_KEY = "synthetic-readonly-key";
    private static final String APP_SECRET = "synthetic-readonly-secret";
    private static final Duration REFRESH = Duration.ofMinutes(1);
    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration REQUEST = Duration.ofSeconds(15);

    @Test
    void permitsMissingCredentialsAndLimitsWhenDisabled() {
        KisStockBasicInfoProperties properties = new KisStockBasicInfoProperties(false, null, null, null, null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.appKey()).isNull();
        assertThat(properties.appSecret()).isNull();
        assertThat(properties.toString()).contains("appKey=<redacted>", "appSecret=<redacted>");
    }

    @Test
    void preservesCredentialsAndValidLimitsWithoutPrintingSecrets() {
        KisStockBasicInfoProperties properties = properties(APP_KEY, APP_SECRET, REFRESH, CONNECT, REQUEST);

        assertThat(properties.appKey()).isEqualTo(APP_KEY);
        assertThat(properties.appSecret()).isEqualTo(APP_SECRET);
        assertThat(properties.tokenRefreshBeforeExpiration()).isEqualTo(REFRESH);
        assertThat(properties.connectTimeout()).isEqualTo(CONNECT);
        assertThat(properties.requestTimeout()).isEqualTo(REQUEST);
        assertThat(String.valueOf(properties)).contains("enabled=true", "appKey=<redacted>", "appSecret=<redacted>")
                .doesNotContain(APP_KEY, APP_SECRET);
    }

    @Test
    void redactsCredentialsEvenWhenDisabled() {
        KisStockBasicInfoProperties properties = new KisStockBasicInfoProperties(
                false, APP_KEY, APP_SECRET, null, null, null
        );

        assertThat(properties.toString()).doesNotContain(APP_KEY, APP_SECRET);
        assertThat(properties.appKey()).isEqualTo(APP_KEY);
        assertThat(properties.appSecret()).isEqualTo(APP_SECRET);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " key", "key ", "ke y", "synthetic-readonly-key\r\nHeader:value", "\u00e9"})
    void rejectsInvalidCredentialsWithoutNormalizingOrExposingThem(String value) {
        assertThatThrownBy(() -> properties(value, APP_SECRET, REFRESH, CONNECT, REQUEST))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("appKey must be nonblank visible ASCII without whitespace.").hasNoCause();
        assertThatThrownBy(() -> properties(APP_KEY, value, REFRESH, CONNECT, REQUEST))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("appSecret must be nonblank visible ASCII without whitespace.").hasNoCause();
    }

    @ParameterizedTest
    @MethodSource("invalidDurations")
    void rejectsMissingOrInvalidDurations(String field, Duration value, String message) {
        assertThatThrownBy(() -> properties(
                APP_KEY, APP_SECRET,
                field.equals("refresh") ? value : REFRESH,
                field.equals("connect") ? value : CONNECT,
                field.equals("request") ? value : REQUEST
        )).isExactlyInstanceOf(IllegalArgumentException.class).hasMessage(message).hasNoCause();
    }

    @Test
    void acceptsZeroRefreshMarginAndEqualOneMillisecondTimeouts() {
        KisStockBasicInfoProperties properties = properties(
                APP_KEY, APP_SECRET, Duration.ZERO, Duration.ofMillis(1), Duration.ofMillis(1)
        );

        assertThat(properties.tokenRefreshBeforeExpiration()).isZero();
        assertThat(properties.connectTimeout()).isEqualTo(properties.requestTimeout());
    }

    @Test
    void rejectsConnectTimeoutLongerThanRequestTimeout() {
        assertThatThrownBy(() -> properties(APP_KEY, APP_SECRET, REFRESH, REQUEST.plusNanos(1), REQUEST))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("connectTimeout must not exceed requestTimeout.");
    }

    private static Stream<Arguments> invalidDurations() {
        return Stream.of(
                Arguments.of("refresh", null, "tokenRefreshBeforeExpiration must not be null or negative."),
                Arguments.of("refresh", Duration.ofNanos(-1), "tokenRefreshBeforeExpiration must not be null or negative."),
                Arguments.of("connect", null, "connectTimeout must be at least 1 millisecond."),
                Arguments.of("connect", Duration.ZERO, "connectTimeout must be at least 1 millisecond."),
                Arguments.of("connect", Duration.ofNanos(-1), "connectTimeout must be at least 1 millisecond."),
                Arguments.of("connect", Duration.ofNanos(999_999), "connectTimeout must be at least 1 millisecond."),
                Arguments.of("request", null, "requestTimeout must be at least 1 millisecond."),
                Arguments.of("request", Duration.ZERO, "requestTimeout must be at least 1 millisecond."),
                Arguments.of("request", Duration.ofNanos(-1), "requestTimeout must be at least 1 millisecond."),
                Arguments.of("request", Duration.ofNanos(999_999), "requestTimeout must be at least 1 millisecond.")
        );
    }

    private KisStockBasicInfoProperties properties(
            String key, String secret, Duration refresh, Duration connect, Duration request
    ) {
        return new KisStockBasicInfoProperties(true, key, secret, refresh, connect, request);
    }
}
