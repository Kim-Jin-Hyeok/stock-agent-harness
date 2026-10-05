package com.stock.market.stock.master.provider.kis.parsing.record;

import java.util.Objects;

public record KisStockMasterRawRecord(
        int lineNumber,
        String symbol,
        String standardCode,
        String name,
        String rawGroup,
        String rawEtp,
        String rawPreferred,
        String rawListingDate,
        String rawSuspension,
        String rawLiquidation,
        String rawBaseDate,
        String rawLine
) {
    public KisStockMasterRawRecord {
        if (lineNumber <= 0 || symbol == null || symbol.isBlank()
                || standardCode == null || standardCode.isBlank()) {
            throw new IllegalArgumentException("Line number and stock master identifiers must be present.");
        }
        Objects.requireNonNull(name, "name must not be null.");
        requireWidth(rawGroup, 2, "rawGroup");
        requireWidth(rawEtp, 1, "rawEtp");
        requireWidth(rawPreferred, 1, "rawPreferred");
        requireWidth(rawListingDate, 8, "rawListingDate");
        requireWidth(rawSuspension, 1, "rawSuspension");
        requireWidth(rawLiquidation, 1, "rawLiquidation");
        requireWidth(rawBaseDate, 8, "rawBaseDate");
        if (rawLine == null || rawLine.isEmpty() || rawLine.indexOf('\n') >= 0 || rawLine.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("rawLine must preserve one payload without a line terminator.");
        }
    }

    private static void requireWidth(String value, int width, String field) {
        if (value == null || value.length() != width) {
            throw new IllegalArgumentException(field + " must preserve its raw field width=" + width + ".");
        }
    }
}
