package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.input;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_SPAC_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRestrictionScreeningResultTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final KisStockBasicInfoRestrictionObservationPolicy observationPolicy = new KisStockBasicInfoRestrictionObservationPolicy();
    private final KisStockBasicInfoRestrictionScreeningPolicy policy = new KisStockBasicInfoRestrictionScreeningPolicy();

    @ParameterizedTest
    @EnumSource(KisStockBasicInfoTypeResolutionReasonCode.class)
    void preservesEntireInputsAcrossJsonRoundTripForAllTypeReasons(KisStockBasicInfoTypeResolutionReasonCode reason) throws Exception {
        var observation = observationPolicy.evaluate(KisStockBasicInfoTypeResolutionFixture.sample(reason));
        assertRoundTrip(policy.evaluate(observation));
    }

    @ParameterizedTest
    @EnumSource(KisStockBasicInfoRestrictionScreeningStatus.class)
    void roundTripsEveryScreeningStatusAndRejectsAnyDifferentStatus(KisStockBasicInfoRestrictionScreeningStatus status) throws Exception {
        var result = switch (status) {
            case EXCLUSION_SIGNAL_OBSERVED -> policy.evaluate(observation(Map.of("spac", "Y")));
            case REVIEW_REQUIRED -> policy.evaluate(observationPolicy.evaluate(input(KOSPI, Map.of(), Map.of())));
            case NO_EXCLUSION_SIGNAL_OBSERVED -> policy.evaluate(observation(Map.of()));
        };
        assertThat(result.status()).isEqualTo(status);
        assertRoundTrip(result);
        for (var other : KisStockBasicInfoRestrictionScreeningStatus.values()) {
            if (other != status) {
                ObjectNode changed = mapper.valueToTree(result);
                changed.put("status", other.name());
                assertThatThrownBy(() -> mapper.treeToValue(changed, KisStockBasicInfoRestrictionScreeningResult.class))
                        .hasRootCauseInstanceOf(IllegalArgumentException.class);
            }
        }
    }

    @Test
    void missingRequestedRecordRemainsAReviewResultAfterJsonRoundTrip() throws Exception {
        var type = KisStockBasicInfoTypeResolutionFixture.policy().resolve(
                KisStockBasicInfoTypeResolutionFixture.matching("999999", "KR7005930003", "STK", "ST", "101"));
        assertRoundTrip(policy.evaluate(observationPolicy.evaluate(type)));
    }

    @Test
    void rejectsRemovedDuplicatedReorderedOrInjectedReasons() {
        var result = policy.evaluate(observationPolicy.evaluate(input(KOSPI,
                Map.of("suspension", "Y", "spac", "Y"), Map.of("admn_item_yn", "?"))));
        var removed = new ArrayList<>(result.reasonCodes());
        removed.removeFirst();
        var duplicate = new ArrayList<>(result.reasonCodes());
        duplicate.add(duplicate.getFirst());
        var reordered = new ArrayList<>(result.reasonCodes());
        Collections.reverse(reordered);
        for (var reasons : List.of(List.<KisStockBasicInfoRestrictionScreeningReasonCode>of(), removed, duplicate, reordered)) {
            assertInvalidReasons(result, reasons);
        }
        for (var reason : KisStockBasicInfoRestrictionScreeningReasonCode.values()) {
            var injected = new ArrayList<>(result.reasonCodes());
            injected.add(reason);
            assertInvalidReasons(result, injected);
        }
    }

    @Test
    void rejectsAReasonFromAnUnmatchedApiAndPromotionOfMasterDiagnostics() {
        var observation = observationPolicy.evaluate(KisStockBasicInfoTypeResolutionFixture.sample(
                KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED));
        var result = policy.evaluate(observation);
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(observation,
                EXCLUSION_SIGNAL_OBSERVED, result.reasonCodes(), result.screeningVersion()))
                .isInstanceOf(IllegalArgumentException.class);
        var reasons = new ArrayList<>(result.reasonCodes());
        reasons.add(KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_MANAGEMENT_Y_OBSERVED);
        assertInvalidReasons(result, reasons);
    }

    @Test
    void rejectsReasonsForAChangedSourceOrFieldEvenWhenTheOverallStatusIsTheSame() {
        var result = policy.evaluate(observation(Map.of("spac", "Y")));
        ObjectNode changed = mapper.valueToTree(result);
        changed.set("observation", mapper.valueToTree(observation(Map.of("suspension", "Y"))));
        assertThatThrownBy(() -> mapper.treeToValue(changed, KisStockBasicInfoRestrictionScreeningResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copiesReasonListAndRejectsNullMembers() {
        var observation = observation(Map.of("spac", "Y"));
        var mutable = new ArrayList<>(List.of(MASTER_SPAC_Y_OBSERVED));
        var result = new KisStockBasicInfoRestrictionScreeningResult(observation, EXCLUSION_SIGNAL_OBSERVED,
                mutable, KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION);
        mutable.clear();
        assertThat(result.reasonCodes()).containsExactly(MASTER_SPAC_Y_OBSERVED);
        assertThatThrownBy(() -> result.reasonCodes().clear()).isInstanceOf(UnsupportedOperationException.class);
        mutable.add(null);
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(observation, EXCLUSION_SIGNAL_OBSERVED,
                mutable, result.screeningVersion())).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullObservationStatusOrReasons() {
        var observation = observation(Map.of());
        String version = KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION;
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(null, NO_EXCLUSION_SIGNAL_OBSERVED,
                List.of(), version)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(observation, null,
                List.of(), version)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(observation, NO_EXCLUSION_SIGNAL_OBSERVED,
                null, version)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankScreeningVersion(String version) {
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(observation(Map.of()),
                NO_EXCLUSION_SIGNAL_OBSERVED, List.of(), version)).isInstanceOf(IllegalArgumentException.class);
    }

    private KisStockBasicInfoRestrictionObservationResult observation(Map<String, String> master) {
        return observationPolicy.evaluate(input(KOSDAQ, master, Map.of()));
    }

    private void assertRoundTrip(KisStockBasicInfoRestrictionScreeningResult result) throws Exception {
        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoRestrictionScreeningResult.class);
        assertThat(restored).isEqualTo(result);
        assertThat(restored.observation()).isEqualTo(result.observation());
    }

    private static void assertInvalidReasons(KisStockBasicInfoRestrictionScreeningResult result,
                                             List<KisStockBasicInfoRestrictionScreeningReasonCode> reasons) {
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(result.observation(), result.status(),
                reasons, result.screeningVersion())).isInstanceOf(IllegalArgumentException.class);
    }
}
