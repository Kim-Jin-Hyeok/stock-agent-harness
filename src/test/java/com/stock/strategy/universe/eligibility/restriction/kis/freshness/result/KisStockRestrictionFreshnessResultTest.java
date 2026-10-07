package com.stock.strategy.universe.eligibility.restriction.kis.freshness.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.MESSAGE;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.NAME;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessReasonCode.MASTER_OBSERVATION_AFTER_EVALUATION;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.TIME_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.EVALUATED_AT;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_BASIC_INFO_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.MAX_MASTER_AGE;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.analysis;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.withTimes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionFreshnessResultTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final KisStockRestrictionFreshnessPolicy policy = new KisStockRestrictionFreshnessPolicy();

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void fullJsonRoundTripsRetainAllSourceEvidenceAndFreshnessStates(KisStockMasterMarket market) throws Exception {
        var original = analysis(market);
        var expired = withTimes(original, EVALUATED_AT.minus(MAX_MASTER_AGE), EVALUATED_AT.minus(MAX_MASTER_AGE).plusSeconds(1),
                EVALUATED_AT.minus(MAX_BASIC_INFO_AGE), EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusSeconds(1));
        var future = withTimes(original, EVALUATED_AT.plusNanos(1), EVALUATED_AT.plusSeconds(1),
                EVALUATED_AT.minusSeconds(10), EVALUATED_AT.minusSeconds(9));
        var request = new KisStockRestrictionFreshnessRequest(EVALUATED_AT, MAX_MASTER_AGE.minusNanos(11), MAX_BASIC_INFO_AGE.minusNanos(19));
        for (var input : List.of(original, expired, future)) {
            var result = policy.evaluate(request, input);

            var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockRestrictionFreshnessResult.class);

            assertThat(restored).isEqualTo(result);
            assertThat(mapper.<JsonNode>valueToTree(restored)).isEqualTo(mapper.<JsonNode>valueToTree(result));
            assertThat(restored.analysisResult()).isEqualTo(input);
            assertThat(restored.analysisResult().basicInfoAnalysis().response().content()).isEqualTo(input.basicInfoAnalysis().response().content());
            assertThat(restored.request()).isEqualTo(request);
        }
        assertThat(policy.evaluate(request, original).status()).isEqualTo(FRESH);
        assertThat(policy.evaluate(request, expired).status()).isEqualTo(EXPIRED);
        assertThat(policy.evaluate(request, future).status()).isEqualTo(TIME_UNVERIFIED);
    }

    @Test
    void copiesReasonListsAndRejectsMutationOrNullMembers() {
        var result = expired();
        var reasons = new ArrayList<>(result.reasonCodes());

        var copied = copy(result, result.status(), reasons, result.freshnessVersion());
        reasons.clear();

        assertThat(copied.reasonCodes()).containsExactlyElementsOf(result.reasonCodes());
        assertThatThrownBy(() -> copied.reasonCodes().clear()).isExactlyInstanceOf(UnsupportedOperationException.class);
        reasons.add(null);
        assertThatThrownBy(() -> copy(result, result.status(), reasons, result.freshnessVersion())).isExactlyInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "analysisResult", "status", "reasonCodes"})
    void rejectsMissingInputsAndVerdictMetadata(String missing) {
        var result = expired();

        assertThatThrownBy(() -> new KisStockRestrictionFreshnessResult(
                missing.equals("request") ? null : result.request(),
                missing.equals("analysisResult") ? null : result.analysisResult(),
                missing.equals("status") ? null : result.status(),
                missing.equals("reasonCodes") ? null : result.reasonCodes(), result.freshnessVersion()))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"removed", "duplicated", "reordered", "added", "status"})
    void rejectsIncompleteReorderedOrForgedVerdicts(String change) {
        var result = expired();
        var reasons = new ArrayList<>(result.reasonCodes());
        switch (change) {
            case "removed" -> reasons.removeFirst();
            case "duplicated" -> reasons.add(reasons.getFirst());
            case "reordered" -> Collections.reverse(reasons);
            case "added" -> reasons.add(MASTER_OBSERVATION_AFTER_EVALUATION);
            default -> {
            }
        }

        assertThatThrownBy(() -> copy(result, change.equals("status") ? FRESH : result.status(), reasons, result.freshnessVersion()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "UNKNOWN"})
    void rejectsBlankOrUnsupportedVersions(String version) {
        var result = expired();

        assertThatThrownBy(() -> copy(result, result.status(), result.reasonCodes(), version)).isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingVersion() {
        var result = expired();

        assertThatThrownBy(() -> copy(result, result.status(), result.reasonCodes(), null)).isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"evaluation", "masterAge", "basicInfoAge"})
    void doesNotReuseReasonsAfterEvaluationInputsChange(String change) {
        var result = expired();
        var changed = new KisStockRestrictionFreshnessRequest(change.equals("evaluation") ? EVALUATED_AT.minusNanos(1) : EVALUATED_AT,
                change.equals("masterAge") ? MAX_MASTER_AGE.plusNanos(1) : MAX_MASTER_AGE,
                change.equals("basicInfoAge") ? MAX_BASIC_INFO_AGE.plusNanos(1) : MAX_BASIC_INFO_AGE);

        assertThatThrownBy(() -> new KisStockRestrictionFreshnessResult(changed, result.analysisResult(), result.status(),
                result.reasonCodes(), result.freshnessVersion())).isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "reasons", "version", "request", "responseTime"})
    void rejectsJsonWithStaleVerdictsOrChangedTiming(String change) {
        var result = expired();
        ObjectNode node = mapper.valueToTree(result);
        switch (change) {
            case "status" -> node.put("status", "FRESH");
            case "reasons" -> node.withArray("reasonCodes").removeAll();
            case "version" -> node.put("freshnessVersion", "UNKNOWN");
            case "request" -> node.set("request", mapper.valueToTree(new KisStockRestrictionFreshnessRequest(
                    EVALUATED_AT, MAX_MASTER_AGE.plusNanos(1), MAX_BASIC_INFO_AGE)));
            case "responseTime" -> ((ObjectNode) node.get("analysisResult").get("basicInfoAnalysis").get("response"))
                    .set("requestStartedAt", mapper.valueToTree(EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusNanos(1)));
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }

        assertThatThrownBy(() -> mapper.treeToValue(node, KisStockRestrictionFreshnessResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(KisStockRestrictionFreshnessReasonCode.class)
    void derivesStatusFromTheReasonCategory(KisStockRestrictionFreshnessReasonCode reason) {
        assertThat(KisStockRestrictionFreshnessStatus.fromReasonCodes(List.of(reason)))
                .isEqualTo(reason.isTimeUnverified() ? TIME_UNVERIFIED : EXPIRED);
    }

    @Test
    void toStringContainsOnlySummaryNotRawResponseOrCompleteSources() {
        var result = policy.evaluate(request(), analysis());

        assertThat(result.toString()).contains("observationId=17", "requestedSymbol=" + result.analysisResult().basicInfoAnalysis().response().requestedSymbol(),
                        "evaluatedAt=" + EVALUATED_AT, "restrictionStatus=NO_EXCLUSION_SIGNAL_OBSERVED", "status=FRESH", "reasonCodes=[]",
                        "freshnessVersion=" + KisStockRestrictionFreshnessPolicy.FRESHNESS_VERSION)
                .doesNotContain(MESSAGE, NAME, "rawLine=", "masterBatch=", "marketWarningObservation=",
                        new String(result.analysisResult().basicInfoAnalysis().response().content(), StandardCharsets.UTF_8));
    }

    private KisStockRestrictionFreshnessResult expired() {
        var input = withTimes(analysis(), EVALUATED_AT.minus(MAX_MASTER_AGE), EVALUATED_AT.minus(MAX_MASTER_AGE).plusSeconds(1),
                EVALUATED_AT.minus(MAX_BASIC_INFO_AGE), EVALUATED_AT.minus(MAX_BASIC_INFO_AGE).plusSeconds(1));
        return policy.evaluate(request(), input);
    }

    private static KisStockRestrictionFreshnessResult copy(KisStockRestrictionFreshnessResult result,
                                                           KisStockRestrictionFreshnessStatus status,
                                                           List<KisStockRestrictionFreshnessReasonCode> reasons, String version) {
        return new KisStockRestrictionFreshnessResult(result.request(), result.analysisResult(), status, reasons, version);
    }
}
