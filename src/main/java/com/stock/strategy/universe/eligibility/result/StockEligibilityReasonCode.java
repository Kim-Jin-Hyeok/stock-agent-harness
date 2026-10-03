package com.stock.strategy.universe.eligibility.result;

public enum StockEligibilityReasonCode {
    ELIGIBILITY_CONFIRMED(StockEligibilityStatus.ELIGIBLE),
    MARKET_NOT_ALLOWED(StockEligibilityStatus.INELIGIBLE),
    UNSUPPORTED_SECURITY_TYPE(StockEligibilityStatus.INELIGIBLE),
    NOT_LISTED_AS_OF(StockEligibilityStatus.INELIGIBLE),
    AS_OF_DATE_UNVERIFIED(StockEligibilityStatus.DATA_UNVERIFIED),
    CURRENT_INFORMATION_ONLY(StockEligibilityStatus.DATA_UNVERIFIED),
    SOURCE_UNVERIFIED(StockEligibilityStatus.DATA_UNVERIFIED),
    INFORMATION_AVAILABILITY_UNVERIFIED(StockEligibilityStatus.DATA_UNVERIFIED),
    INFORMATION_AFTER_CUTOFF(StockEligibilityStatus.DATA_UNVERIFIED),
    MARKET_UNVERIFIED(StockEligibilityStatus.DATA_UNVERIFIED),
    SECURITY_TYPE_UNVERIFIED(StockEligibilityStatus.DATA_UNVERIFIED),
    LISTING_STATUS_UNVERIFIED(StockEligibilityStatus.DATA_UNVERIFIED);

    private final StockEligibilityStatus status;

    StockEligibilityReasonCode(StockEligibilityStatus status) {
        this.status = status;
    }

    public StockEligibilityStatus status() {
        return status;
    }
}
