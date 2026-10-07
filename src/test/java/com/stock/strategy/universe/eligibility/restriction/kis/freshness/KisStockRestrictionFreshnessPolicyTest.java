package com.stock.strategy.universe.eligibility.restriction.kis.freshness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.BASIC_INFO_OBSERVATION_AFTER_EVALUATION;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.BASIC_INFO_OBSERVATION_EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_AFTER_EVALUATION;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_MARKET_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_SOURCE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.analysis;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.combine;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.withMarketTimes;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.withTimes;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionFreshnessPolicyTest {
    private final KisStockRestrictionFreshnessPolicy policy = new KisStockRestrictionFreshnessPolicy();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void acceptsBothCompletedObservationsAndRetainsAllInputs(KisStockMasterMarket market) {
        var input = analysis(market);
        var request = request();

        var result = policy.evaluate(request, input);

        assertThat(result.status()).isEqualTo(FRESH);
        assertThat(result.reasonCodes()).isEmpty();
        assertThat(result.request()).isSameAs(request);
        assertThat(result.analysisResult()).isSameAs(input);
        assertThat(result.freshnessVersion()).isEqualTo(KisStockRestrictionFreshnessPolicy.FRESHNESS_VERSION);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true"})
    void identifiesExpiredSourcesIndependentlyAndPreservesTheirOrder(boolean oldMaster, boolean oldApi) {
        var input = timed(oldMaster ? EVALUATED_AT.minus(MAX_MASTER_AGE).minusNanos(1) : EVALUATED_AT.minusSeconds(300),
                oldApi ? EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).minusNanos(1) : EVALUATED_AT.minusSeconds(100));
        var expected = new ArrayList<KisStockRestrictionFreshnessReasonCode>();
        if (oldMaster) expected.add(MASTER_OBSERVATION_EXPIRED);
        if (oldApi) expected.add(BASIC_INFO_OBSERVATION_EXPIRED);

        var result = policy.evaluate(request(), input);

        assertThat(result.status()).isEqualTo(EXPIRED);
        assertThat(result.reasonCodes()).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest
    @CsvSource({"master,-1,FRESH", "master,0,EXPIRED", "master,1,EXPIRED",
            "api,-1,FRESH", "api,0,EXPIRED", "api,1,EXPIRED"})
    void expiresExactlyAtTheIndependentAgeBoundary(String source, long offset, KisStockRestrictionFreshnessStatus status) {
        var input = timed(source.equals("master") ? EVALUATED_AT.minus(MAX_MASTER_AGE) : EVALUATED_AT.minusSeconds(300),
                source.equals("api") ? EVALUATED_AT.minus(MAX_BASIC_INFO_AGE) : EVALUATED_AT.minusSeconds(100));
        var request = new KisStockRestrictionFreshnessRequest(EVALUATED_AT.plusNanos(offset), MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);

        var result = policy.evaluate(request, input);

        assertThat(result.status()).isEqualTo(status);
        if (status == FRESH) assertThat(result.reasonCodes()).isEmpty();
        else assertThat(result.reasonCodes()).containsExactly(source.equals("master") ? MASTER_OBSERVATION_EXPIRED : BASIC_INFO_OBSERVATION_EXPIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"master", "api"})
    void measuresAgeFromRequestStartNotRecentCompletion(String source) {
        var input = withTimes(analysis(), source.equals("master") ? EVALUATED_AT.minus(MAX_MASTER_AGE).minusSeconds(1) : EVALUATED_AT.minusSeconds(300),
                EVALUATED_AT.minusSeconds(1), source.equals("api") ? EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).minusSeconds(1) : EVALUATED_AT.minusSeconds(100),
                EVALUATED_AT.minusSeconds(1));

        var result = policy.evaluate(request(), input);

        assertThat(result.status()).isEqualTo(EXPIRED);
        assertThat(result.reasonCodes()).containsExactly(source.equals("master") ? MASTER_OBSERVATION_EXPIRED : BASIC_INFO_OBSERVATION_EXPIRED);
    }

    @ParameterizedTest
    @CsvSource({"master,futureStart", "master,straddling", "master,oldStart", "api,futureStart", "api,straddling", "api,oldStart"})
    void treatsUnfinishedOrEntirelyFutureObservationsAsTimeUnverified(String source, String interval) {
        Instant start = switch (interval) {
            case "futureStart" -> EVALUATED_AT.plusNanos(1);
            case "straddling" -> EVALUATED_AT.minusSeconds(1);
            case "oldStart" -> EVALUATED_AT.minus(source.equals("master") ? MAX_MASTER_AGE : MAX_BASIC_INFO_AGE).minusSeconds(1);
            default -> throw new IllegalArgumentException("Unexpected fixture interval.");
        };
        var input = withTimes(analysis(), source.equals("master") ? start : EVALUATED_AT.minusSeconds(300),
                source.equals("master") ? EVALUATED_AT.plusSeconds(1) : EVALUATED_AT.minusSeconds(299),
                source.equals("api") ? start : EVALUATED_AT.minusSeconds(100),
                source.equals("api") ? EVALUATED_AT.plusSeconds(1) : EVALUATED_AT.minusSeconds(99));

        var result = policy.evaluate(request(), input);

        assertThat(result.status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.reasonCodes()).containsExactly(source.equals("master") ? MASTER_OBSERVATION_AFTER_EVALUATION : BASIC_INFO_OBSERVATION_AFTER_EVALUATION);
    }

    @Test
    void acceptsCompletionExactlyAtEvaluationIncludingZeroAgeWithOneNanosecondLimits() {
        var input = withTimes(analysis(), EVALUATED_AT, EVALUATED_AT, EVALUATED_AT, EVALUATED_AT);
        var request = new KisStockRestrictionFreshnessRequest(EVALUATED_AT, Duration.ofNanos(1), Duration.ofNanos(1));

        assertThat(policy.evaluate(request, input).status()).isEqualTo(FRESH);
    }

    @Test
    void preservesBothFutureSourceDiagnostics() {
        var input = withTimes(analysis(), EVALUATED_AT.plusNanos(1), EVALUATED_AT.plusSeconds(1),
                EVALUATED_AT.plusNanos(1), EVALUATED_AT.plusSeconds(1));

        var result = policy.evaluate(request(), input);

        assertThat(result.status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_OBSERVATION_AFTER_EVALUATION, BASIC_INFO_OBSERVATION_AFTER_EVALUATION);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unverifiedTimingTakesPriorityWithoutDiscardingTheOtherSourceExpiration(boolean futureMaster) {
        var input = withTimes(analysis(), futureMaster ? EVALUATED_AT.plusNanos(1) : EVALUATED_AT.minus(MAX_MASTER_AGE),
                futureMaster ? EVALUATED_AT.plusSeconds(1) : EVALUATED_AT.minus(MAX_MASTER_AGE).plusSeconds(1),
                futureMaster ? EVALUATED_AT.minus(MAX_BASIC_INFO_AGE) : EVALUATED_AT.plusNanos(1),
                futureMaster ? EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusSeconds(1) : EVALUATED_AT.plusSeconds(1));

        var result = policy.evaluate(request(), input);

        assertThat(result.status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.reasonCodes()).containsExactly(futureMaster ? MASTER_OBSERVATION_AFTER_EVALUATION : MASTER_OBSERVATION_EXPIRED,
                futureMaster ? BASIC_INFO_OBSERVATION_EXPIRED : BASIC_INFO_OBSERVATION_AFTER_EVALUATION);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void doesNotExpireTheSelectedMarketBecauseTheOtherFileOrBatchStartIsOld(KisStockMasterMarket market) {
        var input = analysis(market);
        var matching = input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult();
        var other = market == KOSPI ? KOSDAQ : KOSPI;
        var master = withMarketTimes(matching.masterBatch(), other, EVALUATED_AT.minus(Duration.ofDays(30)), EVALUATED_AT.minus(Duration.ofDays(29)));
        var changed = combine(input.basicInfoAnalysis().response(), master, warnings(master, market));

        assertThat(policy.evaluate(request(), changed).status()).isEqualTo(FRESH);
    }

    @Test
    void rejectsAnUnfinishedBatchEvenIfTheSelectedFileAlreadyCompleted() {
        var input = analysis(KOSDAQ);
        var master = input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var unfinished = withMarketTimes(master, KOSPI, EVALUATED_AT.minusSeconds(1), EVALUATED_AT.plusNanos(1));
        var changed = combine(input.basicInfoAnalysis().response(), unfinished, warnings(unfinished, KOSDAQ));

        var result = policy.evaluate(request(), changed);

        assertThat(result.status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_OBSERVATION_AFTER_EVALUATION);
    }

    @Test
    void doesNotAssignMarketTimestampsWhenTheRequestedSymbolIsAbsent() {
        var input = analysis();
        var response = input.basicInfoAnalysis().response();
        var absent = new KisStockBasicInfoRawResponse("999999", response.requestStartedAt(), response.responseReceivedAt(), response.httpStatus(), response.content());
        var master = input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var changed = combine(absent, master, input.restrictionScreeningResult().marketWarningObservation());

        var result = policy.evaluate(request(), changed);

        assertThat(result.status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_OBSERVATION_MARKET_UNVERIFIED);
        assertThat(result.analysisResult().restrictionScreeningResult().status()).isEqualTo(KisStockRestrictionScreeningStatus.REVIEW_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"market", "master", "claimedHash"})
    void doesNotBorrowCollectionTimestampsForDisconnectedWarningEvidence(String change) throws Exception {
        var input = analysis();
        var basic = input.basicInfoAnalysis();
        var master = basic.screeningResult().observation().typeResolution().matchingResult().masterBatch();
        var warning = input.restrictionScreeningResult().marketWarningObservation();
        if (change.equals("market")) {
            warning = warnings(master, KOSPI);
        } else if (change.equals("master")) {
            warning = analysis(KOSDAQ, "02", "Y", Map.of(), Map.of()).restrictionScreeningResult().marketWarningObservation();
        } else {
            var mapper = new ObjectMapper().findAndRegisterModules();
            ObjectNode node = mapper.valueToTree(basic.screeningResult());
            ObjectNode batch = (ObjectNode) node.get("observation").get("typeResolution").get("matchingResult").get("masterBatch");
            ((ObjectNode) batch.withArray("marketResults").get(1).withArray("records").get(1)).put("name", "CHANGED NAME");
            var changed = mapper.treeToValue(node, KisStockBasicInfoRestrictionScreeningResult.class);
            basic = new KisStockBasicInfoAnalysisResult(basic.observationId(), basic.response(), changed);
            assertThat(changed.observation().typeResolution().matchingResult().masterBatch().marketResults().get(1).inputSha256())
                    .isEqualTo(warning.source().source().inputSha256());
        }
        var changed = new KisStockRestrictionAnalysisResult(basic, new KisStockRestrictionScreeningPolicy().evaluate(basic.screeningResult(), warning));

        var result = policy.evaluate(request(), changed);

        assertThat(result.status()).isEqualTo(TIME_UNVERIFIED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_OBSERVATION_SOURCE_UNVERIFIED);
        assertThat(result.analysisResult()).isSameAs(changed);
    }

    @ParameterizedTest
    @CsvSource({"00,NO_EXCLUSION_SIGNAL_OBSERVED", "02,EXCLUSION_SIGNAL_OBSERVED", "99,REVIEW_REQUIRED"})
    void freshTimingDoesNotApproveOrChangeTheOriginalRestrictionStatus(String code, KisStockRestrictionScreeningStatus originalStatus) {
        var input = analysis(KOSDAQ, code, "N", Map.of(), Map.of());
        byte[] bytes = input.basicInfoAnalysis().response().content();
        var result = policy.evaluate(request(), input);

        Arrays.fill(result.analysisResult().basicInfoAnalysis().response().content(), (byte) 0);

        assertThat(result.status()).isEqualTo(FRESH);
        assertThat(result.analysisResult()).isSameAs(input);
        assertThat(result.analysisResult().restrictionScreeningResult().status()).isEqualTo(originalStatus);
        assertThat(result.analysisResult().basicInfoAnalysis().response().content()).isEqualTo(bytes);
    }

    @Test
    void freshTimingPreservesAnApiIdentityMismatchAsReview() {
        var input = analysis(KOSDAQ, "00", "N", Map.of(), Map.of("std_pdno", "KR7999999999"));

        var result = policy.evaluate(request(), input);

        assertThat(result.status()).isEqualTo(FRESH);
        assertThat(result.analysisResult().restrictionScreeningResult().status()).isEqualTo(KisStockRestrictionScreeningStatus.REVIEW_REQUIRED);
    }

    @Test
    void supportsLargeLimitsAtExtremeInstantsWithoutExpirationAdditionOverflow() {
        var input = withTimes(analysis(), Instant.MIN, Instant.MIN, Instant.MIN, Instant.MIN);
        var request = new KisStockRestrictionFreshnessRequest(Instant.MAX, Duration.ofSeconds(Long.MAX_VALUE), Duration.ofSeconds(Long.MAX_VALUE));

        assertThat(policy.evaluate(request, input).status()).isEqualTo(FRESH);
    }

    @Test
    void repeatedCallsDoNotMixPriorInputStateOrUseTheWallClock() {
        var input = analysis();
        var expired = new KisStockRestrictionFreshnessRequest(EVALUATED_AT.plus(Duration.ofDays(2)), MAX_MASTER_AGE, MAX_BASIC_INFO_AGE);

        var first = policy.evaluate(request(), input);
        assertThat(policy.evaluate(expired, input).status()).isEqualTo(EXPIRED);

        assertThat(policy.evaluate(request(), input)).isEqualTo(first);
    }

    @Test
    void rejectsNullRequestOrAnalysis() {
        assertThatThrownBy(() -> policy.evaluate(null, analysis())).isExactlyInstanceOf(NullPointerException.class).hasMessage("request must not be null.");
        assertThatThrownBy(() -> policy.evaluate(request(), null)).isExactlyInstanceOf(NullPointerException.class).hasMessage("analysisResult must not be null.");
    }

    @Test
    void statusCalculationRejectsMissingReasonsAndMembers() {
        assertThatThrownBy(() -> KisStockRestrictionFreshnessStatus.fromReasonCodes(null)).isExactlyInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> KisStockRestrictionFreshnessStatus.fromReasonCodes(Arrays.asList((KisStockRestrictionFreshnessReasonCode) null)))
                .isExactlyInstanceOf(NullPointerException.class);
    }

    private static KisStockRestrictionAnalysisResult timed(Instant masterStart, Instant apiStart) {
        return withTimes(analysis(), masterStart, masterStart.plusSeconds(1), apiStart, apiStart.plusSeconds(1));
    }
}
