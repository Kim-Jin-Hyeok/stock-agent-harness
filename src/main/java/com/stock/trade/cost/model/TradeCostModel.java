package com.stock.trade.cost.model;

import java.math.BigDecimal;
import java.util.Objects;

public record TradeCostModel(
        String modelId,
        int modelVersion,
        BigDecimal buyCommissionRate,
        BigDecimal sellCommissionRate,
        BigDecimal sellTaxRate,
        BigDecimal buySlippageRate,
        BigDecimal sellSlippageRate
) {
    private static final BigDecimal ONE = BigDecimal.ONE;

    public TradeCostModel {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException(
                    "modelId must not be blank."
            );
        }
        if (modelVersion < 1) {
            throw new IllegalArgumentException(
                    "modelVersion must be at least 1."
            );
        }

        validateRate("buyCommissionRate", buyCommissionRate);
        validateRate("sellCommissionRate", sellCommissionRate);
        validateRate("sellTaxRate", sellTaxRate);
        validateRate("buySlippageRate", buySlippageRate);
        validateRate("sellSlippageRate", sellSlippageRate);

        if (sellCommissionRate.add(sellTaxRate).compareTo(ONE) >= 0) {
            throw new IllegalArgumentException(
                    "sellCommissionRate and sellTaxRate total must be "
                            + "less than 1."
            );
        }
    }

    private static void validateRate(
            String fieldName,
            BigDecimal rate
    ) {
        Objects.requireNonNull(
                rate,
                fieldName + " must not be null."
        );
        if (rate.signum() < 0 || rate.compareTo(ONE) >= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be at least 0 and less than 1."
            );
        }
    }
}
