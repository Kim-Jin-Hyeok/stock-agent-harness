package com.stock.market.stock.basicinfo.provider.kis;

import com.stock.broker.kis.auth.KisTokenClient;
import com.stock.broker.kis.auth.KisTokenProvider;
import com.stock.broker.kis.auth.dto.KisTokenResponse;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockBasicInfoProviderTest {
    private static final String ACCESS_TOKEN = "synthetic-readonly-token";
    private static final Instant START = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant END = START.plusMillis(53);
    private final KisStockBasicInfoClient client = mock(KisStockBasicInfoClient.class);
    private final KisTokenProvider tokenProvider = mock(KisTokenProvider.class);
    private final KisStockBasicInfoProvider provider = new KisStockBasicInfoProvider(client, tokenProvider);

    @Test
    void constructionDoesNotIssueTokensOrQueryStocks() {
        verifyNoInteractions(client, tokenProvider);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new KisStockBasicInfoProvider(null, tokenProvider))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("client must not be null.");
        assertThatThrownBy(() -> new KisStockBasicInfoProvider(client, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("tokenProvider must not be null.");
        verifyNoInteractions(client, tokenProvider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"005930", "0004Y0"})
    void obtainsTokenBeforeOneLookupAndReturnsOriginalResponseUnchanged(String symbol) {
        var expected = response(symbol, " \n{\"synthetic\":\"raw-content\"}\n ");
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(client.getStockBasicInfo(symbol, ACCESS_TOKEN)).thenReturn(expected);

        var actual = provider.getStockBasicInfo(symbol);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.requestedSymbol()).isEqualTo(symbol);
        assertThat(actual.requestStartedAt()).isEqualTo(START);
        assertThat(actual.responseReceivedAt()).isEqualTo(END);
        assertThat(actual.content()).isEqualTo(expected.content());
        var order = inOrder(tokenProvider, client);
        order.verify(tokenProvider).getAccessToken();
        order.verify(client).getStockBasicInfo(symbol, ACCESS_TOKEN);
        verifyNoMoreInteractions(tokenProvider, client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ", "00593", "0059300", "0004y0", " 005930", "005930 ", "00593?", "00593/",
            "\r\n", "005930\n", "\uFF10\uFF10\uFF15\uFF19\uFF13\uFF10"
    })
    void rejectsInvalidSymbolsBeforeTokenLookupWithoutNormalization(String symbol) {
        assertThatThrownBy(() -> provider.getStockBasicInfo(symbol))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.").hasNoCause();
        verifyNoInteractions(tokenProvider, client);
    }

    @ParameterizedTest
    @MethodSource("tokenFailures")
    void propagatesSanitizedTokenFailureWithoutQueryingOrRetrying(RuntimeException failure) {
        when(tokenProvider.getAccessToken()).thenThrow(failure);

        assertThatThrownBy(() -> provider.getStockBasicInfo("005930")).isSameAs(failure);

        verify(tokenProvider).getAccessToken();
        verifyNoMoreInteractions(tokenProvider);
        verifyNoInteractions(client);
    }

    @Test
    void propagatesSanitizedLookupFailureWithoutRetrying() {
        var failure = new IllegalStateException("KIS stock basic info response HTTP status=503.");
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(client.getStockBasicInfo("005930", ACCESS_TOKEN)).thenThrow(failure);

        assertThatThrownBy(() -> provider.getStockBasicInfo("005930")).isSameAs(failure);

        verify(tokenProvider).getAccessToken();
        verify(client).getStockBasicInfo("005930", ACCESS_TOKEN);
        verifyNoMoreInteractions(tokenProvider, client);
    }

    @Test
    void rejectsNullLookupResponseWithoutRetrying() {
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);

        assertThatThrownBy(() -> provider.getStockBasicInfo("005930"))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS stock basic info response must not be null.").hasNoCause();

        verify(tokenProvider).getAccessToken();
        verify(client).getStockBasicInfo("005930", ACCESS_TOKEN);
        verifyNoMoreInteractions(tokenProvider, client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"rt_cd\":\"1\",\"msg1\":\"Synthetic failure\"}", "not-json"})
    void preservesBusinessFailureAndMalformedJsonWithoutParsingOrRepairing(String content) {
        var expected = response("005930", content);
        when(tokenProvider.getAccessToken()).thenReturn(ACCESS_TOKEN);
        when(client.getStockBasicInfo("005930", ACCESS_TOKEN)).thenReturn(expected);

        assertThat(provider.getStockBasicInfo("005930")).isSameAs(expected);
        verify(tokenProvider).getAccessToken();
        verify(client).getStockBasicInfo("005930", ACCESS_TOKEN);
        verifyNoMoreInteractions(tokenProvider, client);
    }

    @Test
    void reusesExistingTokenCacheAcrossSymbolsWithoutCachingBasicInfoResponses() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        KisTokenProvider cachedTokenProvider = cachedTokenProvider(tokenClient);
        var first = response("005930", "first-response");
        var second = response("0004Y0", "second-response");
        var third = response("005930", "updated-response");
        when(client.getStockBasicInfo("005930", ACCESS_TOKEN)).thenReturn(first, third);
        when(client.getStockBasicInfo("0004Y0", ACCESS_TOKEN)).thenReturn(second);
        var cachedProvider = new KisStockBasicInfoProvider(client, cachedTokenProvider);

        assertThat(cachedProvider.getStockBasicInfo("005930")).isSameAs(first);
        assertThat(cachedProvider.getStockBasicInfo("0004Y0")).isSameAs(second);
        assertThat(cachedProvider.getStockBasicInfo("005930")).isSameAs(third);

        verify(tokenClient).issueToken();
        verify(client, times(2)).getStockBasicInfo("005930", ACCESS_TOKEN);
        verify(client).getStockBasicInfo("0004Y0", ACCESS_TOKEN);
        verifyNoMoreInteractions(tokenClient, client);
        verifyNoInteractions(tokenProvider);
    }

    @Test
    void lookupFailureDoesNotDiscardReusableTokenOrRetryAutomatically() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        KisTokenProvider cachedTokenProvider = cachedTokenProvider(tokenClient);
        var failure = new IllegalStateException("KIS stock basic info request failed.");
        var expected = response("005930", "subsequent-response");
        when(client.getStockBasicInfo("005930", ACCESS_TOKEN)).thenThrow(failure).thenReturn(expected);
        var cachedProvider = new KisStockBasicInfoProvider(client, cachedTokenProvider);

        assertThatThrownBy(() -> cachedProvider.getStockBasicInfo("005930")).isSameAs(failure);
        verify(tokenClient).issueToken();
        verify(client).getStockBasicInfo("005930", ACCESS_TOKEN);

        assertThat(cachedProvider.getStockBasicInfo("005930")).isSameAs(expected);
        verify(tokenClient).issueToken();
        verify(client, times(2)).getStockBasicInfo("005930", ACCESS_TOKEN);
        verifyNoMoreInteractions(tokenClient, client);
        verifyNoInteractions(tokenProvider);
    }

    private KisStockBasicInfoRawResponse response(String symbol, String content) {
        return new KisStockBasicInfoRawResponse(symbol, START, END, 200, content.getBytes(StandardCharsets.UTF_8));
    }

    private KisTokenProvider cachedTokenProvider(KisTokenClient tokenClient) {
        when(tokenClient.issueToken()).thenReturn(new KisTokenResponse(
                ACCESS_TOKEN, "Bearer", 3600L, "2026-10-06 11:00:00"));
        return new KisTokenProvider(tokenClient, Clock.fixed(START, ZoneOffset.UTC), Duration.ofMinutes(1));
    }

    private static Stream<RuntimeException> tokenFailures() {
        return Stream.of(
                new ResourceAccessException("KIS token request failed."),
                new RestClientResponseException("KIS token response HTTP status=429.", 429, "", null, null, null),
                new IllegalStateException("KIS access token expiration is invalid.")
        );
    }
}
