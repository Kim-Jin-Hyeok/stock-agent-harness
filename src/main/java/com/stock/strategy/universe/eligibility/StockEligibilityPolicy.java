package com.stock.strategy.universe.eligibility;

import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockListingStatus;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.eligibility.result.StockEligibilityReasonCode;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StockEligibilityPolicy {
    public StockEligibilityResult evaluate(
            StockEligibilityRequest request,
            StockEligibilityInput input
    ) {
        Objects.requireNonNull(request, "request must not be null.");
        Objects.requireNonNull(input, "input must not be null.");
        if (!request.selectionAsOfDate().equals(input.asOfDate())) {
            return result(request, input, StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
        }
        if (input.evidenceStatus() == StockEligibilityEvidenceStatus.CURRENT_ONLY) {
            return result(request, input, StockEligibilityReasonCode.CURRENT_INFORMATION_ONLY);
        }
        // AS_OF_VERIFIED is a caller assertion, not proof derived from a date or source string.
        if (input.evidenceStatus() != StockEligibilityEvidenceStatus.AS_OF_VERIFIED
                || input.sourceReference() == null || input.sourceReference().isBlank()) {
            return result(request, input, StockEligibilityReasonCode.SOURCE_UNVERIFIED);
        }
        if (input.informationAvailableAt() == null) {
            return result(request, input, StockEligibilityReasonCode.INFORMATION_AVAILABILITY_UNVERIFIED);
        }
        if (input.informationAvailableAt().isAfter(request.selectionCutoffAt())) {
            return result(request, input, StockEligibilityReasonCode.INFORMATION_AFTER_CUTOFF);
        }

        // Require complete point-in-time fields before making a confirmed exclusion.
        if (input.market() == null) {
            return result(request, input, StockEligibilityReasonCode.MARKET_UNVERIFIED);
        }
        if (input.securityType() == null) {
            return result(request, input, StockEligibilityReasonCode.SECURITY_TYPE_UNVERIFIED);
        }
        if (input.listingStatus() == null) {
            return result(request, input, StockEligibilityReasonCode.LISTING_STATUS_UNVERIFIED);
        }
        if (!request.eligibleMarkets().contains(input.market())) {
            return result(request, input, StockEligibilityReasonCode.MARKET_NOT_ALLOWED);
        }
        if (!request.eligibleSecurityTypes().contains(input.securityType())) {
            return result(request, input, StockEligibilityReasonCode.UNSUPPORTED_SECURITY_TYPE);
        }
        if (input.listingStatus() != StockListingStatus.LISTED) {
            return result(request, input, StockEligibilityReasonCode.NOT_LISTED_AS_OF);
        }
        return result(request, input, StockEligibilityReasonCode.ELIGIBILITY_CONFIRMED);
    }

    private static StockEligibilityResult result(
            StockEligibilityRequest request,
            StockEligibilityInput input,
            StockEligibilityReasonCode reasonCode
    ) {
        return new StockEligibilityResult(request, input, reasonCode.status(), reasonCode);
    }
}
