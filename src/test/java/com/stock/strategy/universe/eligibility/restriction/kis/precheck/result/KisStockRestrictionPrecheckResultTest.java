package com.stock.strategy.universe.eligibility.restriction.kis.precheck.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.MESSAGE;
import static com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture.NAME;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.EXPIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy.PRECHECK_VERSION;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus.BLOCKED;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckStatus.CLEAR;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.support.KisStockRestrictionPrecheckFixture.freshness;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionPrecheckResultTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final KisStockRestrictionPrecheckPolicy policy = new KisStockRestrictionPrecheckPolicy();

    @ParameterizedTest
    @CsvSource({
            "NO_EXCLUSION_SIGNAL_OBSERVED,FRESH",
            "NO_EXCLUSION_SIGNAL_OBSERVED,EXPIRED",
            "NO_EXCLUSION_SIGNAL_OBSERVED,TIME_UNVERIFIED",
            "REVIEW_REQUIRED,FRESH",
            "REVIEW_REQUIRED,EXPIRED",
            "REVIEW_REQUIRED,TIME_UNVERIFIED",
            "EXCLUSION_SIGNAL_OBSERVED,FRESH",
            "EXCLUSION_SIGNAL_OBSERVED,EXPIRED",
            "EXCLUSION_SIGNAL_OBSERVED,TIME_UNVERIFIED"
    })
    void roundTripsAllCombinationsAndFullEvidenceForBothMarkets(
            KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) throws Exception {
        for (var market : KisStockMasterMarket.values()) {
            var input = freshness(market, restrictionStatus, freshnessStatus);
            var result = policy.evaluate(input);

            var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockRestrictionPrecheckResult.class);

            assertThat(restored).isEqualTo(result);
            assertThat(mapper.<JsonNode>valueToTree(restored)).isEqualTo(mapper.<JsonNode>valueToTree(result));
            assertThat(restored.freshnessResult()).isEqualTo(input);
            assertThat(restored.freshnessResult().request()).isEqualTo(input.request());
            assertThat(restored.freshnessResult().analysisResult()).isEqualTo(input.analysisResult());
            assertThat(restored.freshnessResult().analysisResult().basicInfoAnalysis().response().content())
                    .isEqualTo(input.analysisResult().basicInfoAnalysis().response().content());
            assertThat(policy.evaluate(restored.freshnessResult())).isEqualTo(result);
        }
    }

    @Test
    void rejectsClearForEveryBlockedCombinationAndBlockedForTheClearCombination() {
        for (var restrictionStatus : KisStockRestrictionScreeningStatus.values()) {
            for (var freshnessStatus : KisStockRestrictionFreshnessStatus.values()) {
                var input = freshness(KOSDAQ, restrictionStatus, freshnessStatus);
                var result = policy.evaluate(input);
                var inconsistent = result.status() == CLEAR ? BLOCKED : CLEAR;

                assertThatThrownBy(() -> new KisStockRestrictionPrecheckResult(input, inconsistent, PRECHECK_VERSION))
                        .isExactlyInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Precheck status must agree with the original restriction and freshness statuses.");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"freshnessResult", "status"})
    void rejectsMissingInputOrStatus(String missing) {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH);

        assertThatThrownBy(() -> new KisStockRestrictionPrecheckResult(
                missing.equals("freshnessResult") ? null : input, missing.equals("status") ? null : CLEAR, PRECHECK_VERSION))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "UNKNOWN", "KIS_STOCK_RESTRICTION_PRECHECK_V2"})
    void rejectsMissingBlankOrUnsupportedVersion(String version) {
        var input = freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH);

        assertThatThrownBy(() -> new KisStockRestrictionPrecheckResult(input, CLEAR, version))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("precheckVersion must be the supported restriction precheck version.");
    }

    @Test
    void keepsNestedReasonsAndRawBytesImmutable() {
        var input = freshness(KOSDAQ, EXCLUSION_SIGNAL_OBSERVED, EXPIRED);
        var result = policy.evaluate(input);
        var response = result.freshnessResult().analysisResult().basicInfoAnalysis().response();
        byte[] bytes = response.content();

        Arrays.fill(response.content(), (byte) 0);

        assertThat(response.content()).isEqualTo(bytes);
        assertThat(result.freshnessResult()).isSameAs(input);
        assertThatThrownBy(() -> result.freshnessResult().reasonCodes().clear()).isExactlyInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.freshnessResult().analysisResult().restrictionScreeningResult().reasonCodes().clear())
                .isExactlyInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "version", "removedRestrictionReasons", "removedFreshnessReasons", "changedEvaluation"})
    void rejectsJsonWithInconsistentOrUnsupportedVerdicts(String change) {
        var result = policy.evaluate(freshness(KOSDAQ, EXCLUSION_SIGNAL_OBSERVED, EXPIRED));
        ObjectNode node = mapper.valueToTree(result);
        ObjectNode nested = (ObjectNode) node.get("freshnessResult");
        switch (change) {
            case "status" -> node.put("status", "CLEAR");
            case "version" -> node.put("precheckVersion", "UNKNOWN");
            case "removedRestrictionReasons" -> ((ObjectNode) nested.get("analysisResult").get("restrictionScreeningResult"))
                    .withArray("reasonCodes").removeAll();
            case "removedFreshnessReasons" -> nested.withArray("reasonCodes").removeAll();
            case "changedEvaluation" -> ((ObjectNode) nested.get("request"))
                    .set("evaluatedAt", mapper.valueToTree(result.freshnessResult().request().evaluatedAt().minusNanos(1)));
            default -> throw new IllegalArgumentException("Unexpected test change.");
        }

        assertThatThrownBy(() -> mapper.treeToValue(node, KisStockRestrictionPrecheckResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringContainsOnlyVerdictSummaryNotResponseBodyOrCompleteSources() {
        var result = policy.evaluate(freshness(KOSDAQ, EXCLUSION_SIGNAL_OBSERVED, EXPIRED));

        assertThat(result.toString()).contains("observationId=17", "requestedSymbol=", "evaluatedAt=",
                        "restrictionStatus=EXCLUSION_SIGNAL_OBSERVED", "restrictionReasons=[MARKET_WARNING_INVESTMENT_WARNING_OBSERVED]",
                        "freshnessStatus=EXPIRED", "freshnessReasons=[MASTER_OBSERVATION_EXPIRED, BASIC_INFO_OBSERVATION_EXPIRED]",
                        "status=BLOCKED", "precheckVersion=" + PRECHECK_VERSION)
                .doesNotContain(MESSAGE, NAME, "rawLine=", "masterBatch=", "marketWarningObservation=",
                        new String(result.freshnessResult().analysisResult().basicInfoAnalysis().response().content(), StandardCharsets.UTF_8));
    }
}
