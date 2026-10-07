package com.stock.strategy.universe.eligibility.restriction.kis.precheck;

import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.BASIC_INFO_OBSERVATION_EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.analysis;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus.BLOCKED;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus.CLEAR;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.support.KisStockRestrictionPrecheckFixture.freshness;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_INVESTMENT_WARNING_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionPrecheckPolicyTest {
    private final KisStockRestrictionPrecheckPolicy policy = new KisStockRestrictionPrecheckPolicy();

    @ParameterizedTest
    @CsvSource({
            "NO_EXCLUSION_SIGNAL_OBSERVED,FRESH,CLEAR",
            "NO_EXCLUSION_SIGNAL_OBSERVED,EXPIRED,BLOCKED",
            "NO_EXCLUSION_SIGNAL_OBSERVED,TIME_UNVERIFIED,BLOCKED",
            "REVIEW_REQUIRED,FRESH,BLOCKED",
            "REVIEW_REQUIRED,EXPIRED,BLOCKED",
            "REVIEW_REQUIRED,TIME_UNVERIFIED,BLOCKED",
            "EXCLUSION_SIGNAL_OBSERVED,FRESH,BLOCKED",
            "EXCLUSION_SIGNAL_OBSERVED,EXPIRED,BLOCKED",
            "EXCLUSION_SIGNAL_OBSERVED,TIME_UNVERIFIED,BLOCKED"
    })
    void evaluatesAllStatusCombinationsForBothMarkets(
            KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus,
            KisStockRestrictionPrecheckStatus expectedStatus
    ) {
        for (var market : KisStockMasterMarket.values()) {
            var input = freshness(market, restrictionStatus, freshnessStatus);

            var result = policy.evaluate(input);

            assertThat(input.analysisResult().restrictionScreeningResult().status()).isEqualTo(restrictionStatus);
            assertThat(input.status()).isEqualTo(freshnessStatus);
            assertThat(result.status()).isEqualTo(expectedStatus);
            assertThat(result.freshnessResult()).isSameAs(input);
            assertThat(result.freshnessResult().request()).isSameAs(input.request());
            assertThat(result.freshnessResult().analysisResult()).isSameAs(input.analysisResult());
            assertThat(result.precheckVersion()).isEqualTo(KisStockRestrictionPrecheckPolicy.PRECHECK_VERSION);
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void keepsRestrictionAndBothExpirationReasonsWithoutTranslation(KisStockMasterMarket market) {
        var input = freshness(market, EXCLUSION_SIGNAL_OBSERVED, EXPIRED);

        var result = policy.evaluate(input);

        assertThat(result.status()).isEqualTo(BLOCKED);
        assertThat(result.freshnessResult().analysisResult().restrictionScreeningResult().reasonCodes())
                .containsExactly(MARKET_WARNING_INVESTMENT_WARNING_OBSERVED);
        assertThat(result.freshnessResult().reasonCodes()).containsExactly(MASTER_OBSERVATION_EXPIRED, BASIC_INFO_OBSERVATION_EXPIRED);
        assertThat(result.freshnessResult().request()).isEqualTo(request());
    }

    @Test
    void blocksFreshObservationsWhenApiIdentityIsUnconfirmed() {
        var analysis = analysis(KOSDAQ, "00", "N", Map.of(), Map.of("std_pdno", "KR7999999999"));
        var input = new KisStockRestrictionFreshnessPolicy().evaluate(request(), analysis);

        var result = policy.evaluate(input);

        assertThat(input.status()).isEqualTo(FRESH);
        assertThat(analysis.restrictionScreeningResult().status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.status()).isEqualTo(BLOCKED);
        assertThat(result.freshnessResult().analysisResult().restrictionScreeningResult().reasonCodes())
                .contains(STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED);
    }

    @Test
    void preservesExplicitEvaluationAndLimitsWithoutUsingTheWallClockOrPriorCallState() {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH);
        var first = policy.evaluate(input);
        var laterRequest = new KisStockRestrictionFreshnessRequest(EVALUATED_AT.plus(Duration.ofDays(2)), MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);
        var later = new KisStockRestrictionFreshnessPolicy().evaluate(laterRequest, input.analysisResult());

        assertThat(first.status()).isEqualTo(CLEAR);
        assertThat(policy.evaluate(later).status()).isEqualTo(BLOCKED);
        assertThat(policy.evaluate(input)).isEqualTo(first);
        assertThat(first.freshnessResult().request()).isSameAs(input.request());
        assertThat(later.request()).isSameAs(laterRequest);
    }

    @Test
    void rejectsNullInputInsteadOfAssumingNoRestriction() {
        assertThatThrownBy(() -> policy.evaluate(null)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("freshnessResult must not be null.");
    }

    @Test
    void statusCalculationRejectsMissingRestrictionOrFreshness() {
        assertThatThrownBy(() -> KisStockRestrictionPrecheckStatus.fromInputs(null, FRESH))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("restrictionStatus must not be null.");
        assertThatThrownBy(() -> KisStockRestrictionPrecheckStatus.fromInputs(NO_EXCLUSION_SIGNAL_OBSERVED, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("freshnessStatus must not be null.");
    }
}
