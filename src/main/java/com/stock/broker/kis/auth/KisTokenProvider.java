package com.stock.broker.kis.auth;

import com.stock.broker.kis.auth.dto.KisTokenResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public class KisTokenProvider {
    private static final ZoneId KIS_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KisTokenClient tokenClient;
    private final Clock clock;
    private final Duration refreshBeforeExpiration;

    private CachedToken cachedToken;

    public KisTokenProvider(
            KisTokenClient tokenClient,
            Clock clock,
            Duration refreshBeforeExpiration
    ) {
        this.tokenClient = Objects.requireNonNull(
                tokenClient,
                "tokenClient must not be null."
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        this.refreshBeforeExpiration = Objects.requireNonNull(
                refreshBeforeExpiration,
                "refreshBeforeExpiration must not be null."
        );
        if (refreshBeforeExpiration.isNegative()) {
            throw new IllegalArgumentException(
                    "refreshBeforeExpiration must not be negative."
            );
        }
    }

    public synchronized String getAccessToken() {
        Instant now = clock.instant();
        if (isReusable(now)) {
            return cachedToken.accessToken();
        }

        CachedToken issuedToken = toCachedToken(tokenClient.issueToken());
        Instant refreshAt = issuedToken.expiresAt().minus(refreshBeforeExpiration);
        if (!now.isBefore(refreshAt)) {
            throw new IllegalStateException(
                    "Issued KIS access token expires too soon."
            );
        }

        cachedToken = issuedToken;
        return cachedToken.accessToken();
    }

    private boolean isReusable(Instant now) {
        return cachedToken != null
                && now.isBefore(
                        cachedToken.expiresAt().minus(refreshBeforeExpiration)
                );
    }

    private CachedToken toCachedToken(KisTokenResponse response) {
        if (response == null) {
            throw new IllegalStateException("KIS token response must not be null.");
        }
        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("KIS access token must not be blank.");
        }

        return new CachedToken(
                response.accessToken(),
                parseExpiration(response.expiresAt())
        );
    }

    private Instant parseExpiration(String expiresAt) {
        try {
            return LocalDateTime.parse(expiresAt, EXPIRATION_FORMATTER)
                    .atZone(KIS_ZONE_ID)
                    .toInstant();
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalStateException(
                    "KIS access token expiration is invalid.",
                    exception
            );
        }
    }

    private record CachedToken(
            String accessToken,
            Instant expiresAt
    ) {
    }
}
