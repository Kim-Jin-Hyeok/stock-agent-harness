package com.stock.broker.kis.auth;

import com.stock.broker.kis.auth.dto.KisTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientResponseException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisTokenProviderTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant REFRESH_AT = Instant.parse("2026-01-01T00:59:00Z");
    private static final Duration REFRESH_BEFORE_EXPIRATION = Duration.ofMinutes(1);

    @Test
    void issuesTokenWhenCacheIsEmpty() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenReturn(token("access-token"));
        KisTokenProvider provider = provider(tokenClient, fixedClock(ISSUED_AT));

        String accessToken = provider.getAccessToken();

        assertThat(accessToken).isEqualTo("access-token");
        verify(tokenClient).issueToken();
    }

    @Test
    void reusesTokenBeforeRefreshTime() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenReturn(token("access-token"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(
                ISSUED_AT,
                Instant.parse("2026-01-01T00:30:00Z")
        );
        KisTokenProvider provider = provider(tokenClient, clock);

        String firstToken = provider.getAccessToken();
        String secondToken = provider.getAccessToken();

        assertThat(firstToken).isEqualTo("access-token");
        assertThat(secondToken).isEqualTo("access-token");
        verify(tokenClient).issueToken();
    }

    @Test
    void issuesNewTokenWhenRefreshTimeIsReached() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken())
                .thenReturn(token("first-token"))
                .thenReturn(token("second-token", "2026-01-01 11:00:00"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(ISSUED_AT, REFRESH_AT);
        KisTokenProvider provider = provider(tokenClient, clock);

        String firstToken = provider.getAccessToken();
        String secondToken = provider.getAccessToken();

        assertThat(firstToken).isEqualTo("first-token");
        assertThat(secondToken).isEqualTo("second-token");
        verify(tokenClient, times(2)).issueToken();
    }

    @Test
    void reusesTokenUntilImmediatelyBeforeRefreshTime() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenReturn(token("access-token"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(ISSUED_AT, REFRESH_AT.minusNanos(1));
        KisTokenProvider provider = provider(tokenClient, clock);

        assertThat(provider.getAccessToken()).isEqualTo("access-token");
        assertThat(provider.getAccessToken()).isEqualTo("access-token");
        verify(tokenClient).issueToken();
    }

    @Test
    void rejectsTokenResponseWithoutAccessToken() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenReturn(token(" "));
        KisTokenProvider provider = provider(tokenClient, fixedClock(ISSUED_AT));

        assertThatIllegalStateException()
                .isThrownBy(provider::getAccessToken)
                .withMessage("KIS access token must not be blank.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "test-app-key test-app-secret test-access-token"})
    void rejectsTokenResponseWithInvalidExpirationWithoutLeakingIt(String expiresAt) {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenReturn(
                token("test-access-token", expiresAt)
        );
        KisTokenProvider provider = provider(tokenClient, fixedClock(ISSUED_AT));

        assertThatThrownBy(provider::getAccessToken)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("KIS access token expiration is invalid.")
                .hasNoCause()
                .satisfies(failure -> {
                    assertThat(failure.getSuppressed()).isEmpty();
                    StringWriter stackTrace = new StringWriter();
                    failure.printStackTrace(new PrintWriter(stackTrace));
                    assertThat(stackTrace.toString()).doesNotContain("test-app-key", "test-app-secret", "test-access-token");
                });
        verify(tokenClient).issueToken();
    }

    @Test
    void doesNotCacheInvalidResponseOrAutomaticallyRetry() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken())
                .thenReturn(token("invalid-token", "invalid-expiration"))
                .thenReturn(token("valid-token"));
        KisTokenProvider provider = provider(tokenClient, fixedClock(ISSUED_AT));

        assertThatIllegalStateException().isThrownBy(provider::getAccessToken)
                .withMessage("KIS access token expiration is invalid.");
        verify(tokenClient).issueToken();
        assertThat(provider.getAccessToken()).isEqualTo("valid-token");
        assertThat(provider.getAccessToken()).isEqualTo("valid-token");
        verify(tokenClient, times(2)).issueToken();
    }

    @Test
    void propagatesSanitizedClientFailureWithoutAutomaticallyRetrying() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenThrow(new RestClientResponseException(
                "KIS token response HTTP status=429.", 429, "", null, null, null
        ));
        KisTokenProvider provider = provider(tokenClient, fixedClock(ISSUED_AT));

        assertThatThrownBy(provider::getAccessToken)
                .isExactlyInstanceOf(RestClientResponseException.class)
                .hasMessage("KIS token response HTTP status=429.").hasNoCause();
        verify(tokenClient).issueToken();
    }

    @Test
    void rejectsTokenThatExpiresBeforeRefreshTime() {
        KisTokenClient tokenClient = mock(KisTokenClient.class);
        when(tokenClient.issueToken()).thenReturn(
                token("access-token", "2026-01-01 09:00:30")
        );
        KisTokenProvider provider = provider(tokenClient, fixedClock(ISSUED_AT));

        assertThatIllegalStateException()
                .isThrownBy(provider::getAccessToken)
                .withMessage("Issued KIS access token expires too soon.");
    }

    private KisTokenProvider provider(KisTokenClient tokenClient, Clock clock) {
        return new KisTokenProvider(
                tokenClient,
                clock,
                REFRESH_BEFORE_EXPIRATION
        );
    }

    private Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private KisTokenResponse token(String accessToken) {
        return token(accessToken, "2026-01-01 10:00:00");
    }

    private KisTokenResponse token(String accessToken, String expiresAt) {
        return new KisTokenResponse(
                accessToken,
                "Bearer",
                3_600L,
                expiresAt
        );
    }
}
