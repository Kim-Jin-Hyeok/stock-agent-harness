package com.stock.market.stock.master.provider.krx.parsing.record;

import java.util.Objects;

public record KrxStockBasicInfoRawRecord(
        int rowNumber,
        String standardCode,
        String symbol,
        String name,
        String abbreviatedName,
        String englishName,
        String rawListingDate,
        String rawMarket,
        String rawSecurityGroup,
        String rawSection,
        String rawStockKind,
        String rawParValue,
        String rawListedShares
) {
    public KrxStockBasicInfoRawRecord {
        if (rowNumber <= 0) {
            throw new IllegalArgumentException("rowNumber must be positive.");
        }
        Objects.requireNonNull(standardCode, "standardCode must not be null.");
        Objects.requireNonNull(symbol, "symbol must not be null.");
        Objects.requireNonNull(name, "name must not be null.");
        Objects.requireNonNull(abbreviatedName, "abbreviatedName must not be null.");
        Objects.requireNonNull(englishName, "englishName must not be null.");
        Objects.requireNonNull(rawListingDate, "rawListingDate must not be null.");
        Objects.requireNonNull(rawMarket, "rawMarket must not be null.");
        Objects.requireNonNull(rawSecurityGroup, "rawSecurityGroup must not be null.");
        Objects.requireNonNull(rawSection, "rawSection must not be null.");
        Objects.requireNonNull(rawStockKind, "rawStockKind must not be null.");
        Objects.requireNonNull(rawParValue, "rawParValue must not be null.");
        Objects.requireNonNull(rawListedShares, "rawListedShares must not be null.");
    }
}
