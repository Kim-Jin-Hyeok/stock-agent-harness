package com.stock.market.stock.basicinfo.observation.persistence;

import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

@Entity
@Table(name = "kis_stock_basic_info_observation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KisStockBasicInfoObservationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_symbol", nullable = false, updatable = false, length = 6)
    private String requestedSymbol;

    @Column(name = "http_status", nullable = false, updatable = false)
    private int httpStatus;

    @Column(name = "request_started_at", nullable = false, updatable = false)
    private Instant requestStartedAt;

    @Column(name = "response_received_at", nullable = false, updatable = false)
    private Instant responseReceivedAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "content_length", nullable = false, updatable = false)
    private int contentLength;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Lob
    @Getter(AccessLevel.NONE)
    @Column(name = "raw_content", nullable = false, updatable = false, length = Integer.MAX_VALUE)
    private byte[] rawContent;

    public static KisStockBasicInfoObservationEntity from(KisStockBasicInfoRawResponse response, Instant recordedAt) {
        Objects.requireNonNull(response, "response must not be null.");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null.");
        var entity = new KisStockBasicInfoObservationEntity();
        entity.requestedSymbol = response.requestedSymbol();
        entity.httpStatus = response.httpStatus();
        // Normalize before JDBC binding so database rounding cannot change the stored observation times.
        entity.requestStartedAt = response.requestStartedAt().truncatedTo(ChronoUnit.MICROS);
        entity.responseReceivedAt = response.responseReceivedAt().truncatedTo(ChronoUnit.MICROS);
        entity.recordedAt = recordedAt.truncatedTo(ChronoUnit.MICROS);
        entity.rawContent = response.content();
        entity.contentLength = entity.rawContent.length;
        entity.contentSha256 = sha256(entity.rawContent);
        return entity;
    }

    public byte[] getRawContent() {
        return rawContent == null ? null : rawContent.clone();
    }

    public KisStockBasicInfoRawResponse toRawResponse() {
        if (rawContent == null || rawContent.length == 0 || rawContent.length > KisStockBasicInfoParser.MAX_CONTENT_BYTES
                || contentLength != rawContent.length || !sha256(rawContent).equals(contentSha256)) {
            throw new IllegalStateException("Stored KIS stock basic info content integrity check failed. id=" + id);
        }
        if (!hasMicrosecondPrecision(requestStartedAt) || !hasMicrosecondPrecision(responseReceivedAt)
                || !hasMicrosecondPrecision(recordedAt)) {
            throw metadataFailure();
        }
        try {
            return new KisStockBasicInfoRawResponse(requestedSymbol, requestStartedAt, responseReceivedAt, httpStatus, rawContent);
        } catch (IllegalArgumentException | NullPointerException failure) {
            // Stored metadata is untrusted; do not expose its values or repair invalid observations.
            throw metadataFailure();
        }
    }

    private IllegalStateException metadataFailure() {
        return new IllegalStateException("Stored KIS stock basic info metadata is invalid. id=" + id);
    }

    private static boolean hasMicrosecondPrecision(Instant value) {
        return value != null && value.getNano() % 1000 == 0;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available.", failure);
        }
    }
}
