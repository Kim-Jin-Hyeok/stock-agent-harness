package com.stock.market.stock.master.provider.kis.parsing.warning.record;

public record KisStockMasterMarketWarningRawRecord(
        int lineNumber,
        String symbol,
        String standardCode,
        String rawMarketWarningCode,
        String rawMarketWarningRiskPreannouncement
) {
    public KisStockMasterMarketWarningRawRecord {
        if (lineNumber <= 0 || symbol == null || symbol.isBlank() || standardCode == null || standardCode.isBlank()) {
            throw new IllegalArgumentException("Line number and market warning identifiers must be present.");
        }
        requireAsciiWidth(rawMarketWarningCode, 2, "rawMarketWarningCode");
        requireAsciiWidth(rawMarketWarningRiskPreannouncement, 1, "rawMarketWarningRiskPreannouncement");
    }

    private static void requireAsciiWidth(String value, int width, String field) {
        if (value == null || value.length() != width || value.chars().anyMatch(character -> character < 32 || character > 126)) {
            throw new IllegalArgumentException(field + " must preserve its printable ASCII field width=" + width + ".");
        }
    }
}
