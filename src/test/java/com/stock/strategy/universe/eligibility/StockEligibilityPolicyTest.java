package com.stock.strategy.universe.eligibility;

import com.stock.strategy.universe.eligibility.input.StockEligibilityEvidenceStatus;
import com.stock.strategy.universe.eligibility.input.StockEligibilityInput;
import com.stock.strategy.universe.eligibility.input.StockListingStatus;
import com.stock.strategy.universe.eligibility.input.StockMarket;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.request.StockEligibilityRequest;
import com.stock.strategy.universe.eligibility.result.StockEligibilityReasonCode;
import com.stock.strategy.universe.eligibility.result.StockEligibilityResult;
import com.stock.strategy.universe.eligibility.result.StockEligibilityStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockEligibilityPolicyTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 23);
    private static final Instant CUTOFF = Instant.parse("2026-09-23T09:00:00Z");
    private static final String SOURCE = "synthetic-as-of-source-01";
    private final StockEligibilityPolicy policy = new StockEligibilityPolicy();
    private final StockEligibilityRequest request = new StockEligibilityRequest(
            DATE, CUTOFF, Set.of(StockMarket.KOSPI, StockMarket.KOSDAQ),
            Set.of(StockSecurityType.COMMON_STOCK)
    );

    @ParameterizedTest
    @CsvSource({"KOSPI, COMMON_STOCK", "KOSDAQ, COMMON_STOCK", "KOSPI, PREFERRED_STOCK", "KOSDAQ, PREFERRED_STOCK"})
    void acceptsExplicitlyAllowedIndividualStocksWithPointInTimeInputs(
            StockMarket market, StockSecurityType type
    ) {
        StockEligibilityRequest criteria = new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(market), Set.of(type)
        );
        StockEligibilityInput input = input(
                DATE, market, type, StockListingStatus.LISTED, SOURCE, CUTOFF,
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED
        );

        StockEligibilityResult result = policy.evaluate(criteria, input);

        assertThat(result.status()).isEqualTo(StockEligibilityStatus.ELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo(StockEligibilityReasonCode.ELIGIBILITY_CONFIRMED);
        assertThat(result.request()).isSameAs(criteria);
        assertThat(result.input()).isSameAs(input);
        assertThat(result.input().symbol()).isEqualTo("005930");
    }

    @ParameterizedTest
    @EnumSource(value = StockSecurityType.class, names = {"PREFERRED_STOCK", "ETF", "ETN", "OTHER"})
    void rejectsConfirmedTypesOutsideExplicitAllowedSet(StockSecurityType type) {
        assertResult(input(DATE, StockMarket.KOSPI, type, StockListingStatus.LISTED,
                SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.INELIGIBLE, StockEligibilityReasonCode.UNSUPPORTED_SECURITY_TYPE);
    }

    @ParameterizedTest
    @EnumSource(value = StockMarket.class, names = {"KOSDAQ", "OTHER"})
    void rejectsConfirmedMarketOutsideExplicitAllowedSet(StockMarket market) {
        StockEligibilityRequest criteria = new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(StockMarket.KOSPI), Set.of(StockSecurityType.COMMON_STOCK)
        );
        StockEligibilityInput input = input(DATE, market, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);

        StockEligibilityResult result = policy.evaluate(criteria, input);

        assertThat(result.status()).isEqualTo(StockEligibilityStatus.INELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo(StockEligibilityReasonCode.MARKET_NOT_ALLOWED);
    }

    @Test
    void rejectsConfirmedNotListedStateAtSelectionDate() {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.NOT_LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.INELIGIBLE, StockEligibilityReasonCode.NOT_LISTED_AS_OF);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-09-22", "2026-09-24"})
    void doesNotReuseUndatedStaleOrFutureDatedInformation(String date) {
        assertResult(input(date == null ? null : LocalDate.parse(date), StockMarket.KOSPI,
                StockSecurityType.COMMON_STOCK, StockListingStatus.LISTED, SOURCE, CUTOFF,
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = StockEligibilityEvidenceStatus.class, names = {"UNVERIFIED"})
    void dateSourceReferenceAndFieldsAloneDoNotProveHistoricalAvailability(
            StockEligibilityEvidenceStatus evidence
    ) {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF, evidence),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.SOURCE_UNVERIFIED);
    }

    @Test
    void doesNotAcceptCurrentOnlyInformationRelabeledWithAnOldDate() {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.CURRENT_ONLY),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.CURRENT_INFORMATION_ONLY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void verificationFlagDoesNotReplaceMissingSourceReference(String source) {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, source, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.SOURCE_UNVERIFIED);
    }

    @Test
    void requiresInformationAvailabilityTime() {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, null, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED,
                StockEligibilityReasonCode.INFORMATION_AVAILABILITY_UNVERIFIED);
    }

    @Test
    void blocksInformationOneNanosecondAfterCutoffEvenIfItWouldExcludeTheStock() {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.ETF,
                StockListingStatus.NOT_LISTED, SOURCE, CUTOFF.plusNanos(1),
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.INFORMATION_AFTER_CUTOFF);
    }

    @Test
    void acceptsInformationKnownBeforeCutoffWithoutRequiringSameDayPublication() {
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF.minusSeconds(86400),
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.ELIGIBLE, StockEligibilityReasonCode.ELIGIBILITY_CONFIRMED);
    }

    @Test
    void usesExplicitCutoffRatherThanAutomaticallyRejectingLaterPublicationForHistoricalAsOfFields() {
        StockEligibilityRequest laterCutoff = new StockEligibilityRequest(
                DATE, CUTOFF.plusSeconds(86400), request.eligibleMarkets(), request.eligibleSecurityTypes()
        );
        StockEligibilityInput historical = input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF.plusSeconds(3600),
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED);

        assertThat(policy.evaluate(request, historical).reasonCode())
                .isEqualTo(StockEligibilityReasonCode.INFORMATION_AFTER_CUTOFF);
        assertThat(policy.evaluate(laterCutoff, historical).status())
                .isEqualTo(StockEligibilityStatus.ELIGIBLE);
    }

    @Test
    void missingInputRetainsOriginalSymbolAndValuesWithFirstUnverifiedReason() {
        StockEligibilityInput missing = new StockEligibilityInput(
                "000660", null, null, null, null, null, null, null
        );
        StockEligibilityResult result = policy.evaluate(request, missing);

        assertThat(result.status()).isEqualTo(StockEligibilityStatus.DATA_UNVERIFIED);
        assertThat(result.reasonCode()).isEqualTo(StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
        assertThat(result.input()).isSameAs(missing);
        assertThat(result.input().symbol()).isEqualTo("000660");
        assertThat(result.input().listingStatus()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "market, MARKET_UNVERIFIED",
            "type, SECURITY_TYPE_UNVERIFIED",
            "listing, LISTING_STATUS_UNVERIFIED"
    })
    void distinguishesMissingMetadataFromConfirmedExclusion(
            String missing, StockEligibilityReasonCode reason
    ) {
        assertResult(input(DATE, missing.equals("market") ? null : StockMarket.KOSPI,
                missing.equals("type") ? null : StockSecurityType.COMMON_STOCK,
                missing.equals("listing") ? null : StockListingStatus.LISTED,
                SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, reason);
    }

    @Test
    void checksAllRequiredFieldsBeforeConfirmedExclusion() {
        assertResult(input(DATE, StockMarket.OTHER, StockSecurityType.ETF, null,
                SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.LISTING_STATUS_UNVERIFIED);
    }

    @Test
    void missingOrFutureEvidenceTakesPriorityOverKnownIneligibleFields() {
        assertResult(input(DATE, StockMarket.OTHER, StockSecurityType.ETF,
                StockListingStatus.NOT_LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.UNVERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.SOURCE_UNVERIFIED);
        assertResult(input(DATE.plusDays(1), StockMarket.OTHER, StockSecurityType.ETF,
                StockListingStatus.NOT_LISTED, SOURCE, CUTOFF.plusSeconds(86400),
                StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.DATA_UNVERIFIED, StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
    }

    @Test
    void confirmedExclusionReasonOrderIsDeterministic() {
        assertResult(input(DATE, StockMarket.OTHER, StockSecurityType.ETF,
                StockListingStatus.NOT_LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.INELIGIBLE, StockEligibilityReasonCode.MARKET_NOT_ALLOWED);
        assertResult(input(DATE, StockMarket.KOSPI, StockSecurityType.ETF,
                StockListingStatus.NOT_LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED),
                StockEligibilityStatus.INELIGIBLE, StockEligibilityReasonCode.UNSUPPORTED_SECURITY_TYPE);
    }

    @Test
    void laterDelistingInformationDoesNotOverwritePastEligibilityOrChangeThePastInput() {
        StockEligibilityInput historical = input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
        StockEligibilityResult pastResult = policy.evaluate(request, historical);
        LocalDate laterDate = DATE.plusDays(10);
        Instant laterTime = CUTOFF.plusSeconds(864000);
        StockEligibilityInput laterDelisting = input(laterDate, StockMarket.KOSPI,
                StockSecurityType.COMMON_STOCK, StockListingStatus.NOT_LISTED,
                "synthetic-later-source", laterTime, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);

        StockEligibilityResult cannotReplacePast = policy.evaluate(request, laterDelisting);
        StockEligibilityRequest laterRequest = new StockEligibilityRequest(
                laterDate, laterTime, request.eligibleMarkets(), request.eligibleSecurityTypes()
        );
        StockEligibilityResult laterResult = policy.evaluate(laterRequest, laterDelisting);

        assertThat(pastResult.status()).isEqualTo(StockEligibilityStatus.ELIGIBLE);
        assertThat(cannotReplacePast.status()).isEqualTo(StockEligibilityStatus.DATA_UNVERIFIED);
        assertThat(cannotReplacePast.reasonCode()).isEqualTo(StockEligibilityReasonCode.AS_OF_DATE_UNVERIFIED);
        assertThat(laterResult.status()).isEqualTo(StockEligibilityStatus.INELIGIBLE);
        assertThat(laterResult.reasonCode()).isEqualTo(StockEligibilityReasonCode.NOT_LISTED_AS_OF);
        assertThat(historical.listingStatus()).isEqualTo(StockListingStatus.LISTED);
        assertThat(policy.evaluate(request, historical)).isEqualTo(pastResult);
    }

    @Test
    void reusesPolicyWithoutRetainingEarlierRequestOrOutcome() {
        StockEligibilityInput preferred = input(DATE, StockMarket.KOSDAQ, StockSecurityType.PREFERRED_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);
        StockEligibilityRequest preferredOnly = new StockEligibilityRequest(
                DATE, CUTOFF, Set.of(StockMarket.KOSDAQ), Set.of(StockSecurityType.PREFERRED_STOCK)
        );

        assertThat(policy.evaluate(request, preferred).status()).isEqualTo(StockEligibilityStatus.INELIGIBLE);
        assertThat(policy.evaluate(preferredOnly, preferred).status()).isEqualTo(StockEligibilityStatus.ELIGIBLE);
        assertThat(policy.evaluate(request, preferred).status()).isEqualTo(StockEligibilityStatus.INELIGIBLE);
    }

    @Test
    void requiresRequestAndInputInsteadOfReturningEmptyOrEligibleResult() {
        StockEligibilityInput input = input(DATE, StockMarket.KOSPI, StockSecurityType.COMMON_STOCK,
                StockListingStatus.LISTED, SOURCE, CUTOFF, StockEligibilityEvidenceStatus.AS_OF_VERIFIED);

        assertThatThrownBy(() -> policy.evaluate(null, input))
                .isInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        assertThatThrownBy(() -> policy.evaluate(request, null))
                .isInstanceOf(NullPointerException.class).hasMessage("input must not be null.");
    }

    private StockEligibilityInput input(
            LocalDate date, StockMarket market, StockSecurityType type,
            StockListingStatus listing, String source, Instant availableAt,
            StockEligibilityEvidenceStatus evidence
    ) {
        return new StockEligibilityInput("005930", date, market, type, listing, source, availableAt, evidence);
    }

    private void assertResult(
            StockEligibilityInput input,
            StockEligibilityStatus status,
            StockEligibilityReasonCode reason
    ) {
        StockEligibilityResult result = policy.evaluate(request, input);
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.request()).isSameAs(request);
        assertThat(result.input()).isSameAs(input);
    }
}
