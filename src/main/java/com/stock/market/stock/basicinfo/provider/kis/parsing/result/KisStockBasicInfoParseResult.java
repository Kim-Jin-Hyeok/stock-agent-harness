package com.stock.market.stock.basicinfo.provider.kis.parsing.result;

import com.stock.market.stock.basicinfo.provider.kis.parsing.record.KisStockBasicInfoRawRecord;

import java.util.Objects;

public record KisStockBasicInfoParseResult(
        String inputSha256,
        String parserVersion,
        String responseCode,
        String messageCode,
        String message,
        KisStockBasicInfoRawRecord rawRecord
) {
    public KisStockBasicInfoParseResult {
        if (inputSha256 == null || !inputSha256.matches("[0-9a-f]{64}")
                || parserVersion == null || parserVersion.isBlank()) {
            throw new IllegalArgumentException("Input SHA-256 and parser version must be present.");
        }
        if (!"0".equals(responseCode)) {
            throw new IllegalArgumentException("Response code must be the success string 0.");
        }
        Objects.requireNonNull(messageCode, "messageCode must not be null.");
        Objects.requireNonNull(message, "message must not be null.");
        Objects.requireNonNull(rawRecord, "rawRecord must not be null.");
    }
}
