package com.stock.broker.kis.account.dto;

import java.math.BigDecimal;

final class KisAccountBalanceNumberParser {

    private KisAccountBalanceNumberParser() {
    }

    static long parseNonNegativeLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }

        final long parsedValue;
        try {
            parsedValue = new BigDecimal(value).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be an integer number.",
                    exception
            );
        }

        if (parsedValue < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
        return parsedValue;
    }
}
