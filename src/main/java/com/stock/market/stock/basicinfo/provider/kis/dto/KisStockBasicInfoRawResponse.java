package com.stock.market.stock.basicinfo.provider.kis.dto;

import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record KisStockBasicInfoRawResponse(
        String requestedSymbol,
        Instant requestStartedAt,
        Instant responseReceivedAt,
        int httpStatus,
        byte[] content
) {
    public KisStockBasicInfoRawResponse {
        if (requestedSymbol == null || !requestedSymbol.matches("[0-9A-Z]{6}")) {
            throw new IllegalArgumentException("requestedSymbol must be exactly 6 uppercase alphanumeric characters.");
        }
        Objects.requireNonNull(requestStartedAt, "requestStartedAt must not be null.");
        Objects.requireNonNull(responseReceivedAt, "responseReceivedAt must not be null.");
        if (responseReceivedAt.isBefore(requestStartedAt)) {
            throw new IllegalArgumentException("responseReceivedAt must not precede requestStartedAt.");
        }
        if (httpStatus != 200) {
            throw new IllegalArgumentException("httpStatus must be 200.");
        }
        Objects.requireNonNull(content, "content must not be null.");
        if (content.length == 0 || content.length > KisStockBasicInfoParser.MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("content must be nonempty and at most 1 MiB.");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KisStockBasicInfoRawResponse response
                && requestedSymbol.equals(response.requestedSymbol)
                && requestStartedAt.equals(response.requestStartedAt)
                && responseReceivedAt.equals(response.responseReceivedAt)
                && httpStatus == response.httpStatus
                && Arrays.equals(content, response.content);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(requestedSymbol, requestStartedAt, responseReceivedAt, httpStatus)
                + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "KisStockBasicInfoRawResponse[requestedSymbol=" + requestedSymbol
                + ", requestStartedAt=" + requestStartedAt + ", responseReceivedAt=" + responseReceivedAt
                + ", httpStatus=" + httpStatus + ", contentLength=" + content.length + "]";
    }
}
