package com.stock.market.stock.basicinfo.provider.kis.parsing.record;

import java.util.Objects;

public record KisStockBasicInfoRawRecord(
        String productNumber,
        String standardCode,
        String name,
        String rawProductType,
        String rawMarket,
        String rawSecurityGroup,
        String rawStockKind,
        String rawSuspension,
        String rawManagement,
        String rawKospiListingDate,
        String rawKospiDelistingDate,
        String rawKosdaqListingDate,
        String rawKosdaqDelistingDate,
        String rawDelistingDate,
        String rawNxtSuspension,
        String rawCompetitiveTradingPermission
) {
    public KisStockBasicInfoRawRecord {
        Objects.requireNonNull(productNumber, "productNumber must not be null.");
        Objects.requireNonNull(standardCode, "standardCode must not be null.");
        Objects.requireNonNull(name, "name must not be null.");
        Objects.requireNonNull(rawProductType, "rawProductType must not be null.");
        Objects.requireNonNull(rawMarket, "rawMarket must not be null.");
        Objects.requireNonNull(rawSecurityGroup, "rawSecurityGroup must not be null.");
        Objects.requireNonNull(rawStockKind, "rawStockKind must not be null.");
        Objects.requireNonNull(rawSuspension, "rawSuspension must not be null.");
        Objects.requireNonNull(rawManagement, "rawManagement must not be null.");
        Objects.requireNonNull(rawKospiListingDate, "rawKospiListingDate must not be null.");
        Objects.requireNonNull(rawKospiDelistingDate, "rawKospiDelistingDate must not be null.");
        Objects.requireNonNull(rawKosdaqListingDate, "rawKosdaqListingDate must not be null.");
        Objects.requireNonNull(rawKosdaqDelistingDate, "rawKosdaqDelistingDate must not be null.");
        Objects.requireNonNull(rawDelistingDate, "rawDelistingDate must not be null.");
        Objects.requireNonNull(rawNxtSuspension, "rawNxtSuspension must not be null.");
        Objects.requireNonNull(rawCompetitiveTradingPermission, "rawCompetitiveTradingPermission must not be null.");
    }
}
