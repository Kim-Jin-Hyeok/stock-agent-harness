package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.RESULT_FIELDS;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.input;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.statuses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRestrictionObservationResultTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final KisStockBasicInfoRestrictionObservationPolicy policy = new KisStockBasicInfoRestrictionObservationPolicy();

    @ParameterizedTest
    @MethodSource("fields")
    void acceptsOnlyTheStatusConsistentWithEachRawField(String field) throws Exception {
        var result = policy.evaluate(input(KOSPI, Map.of("suspension", "Y", "spac", "?"), Map.of("admn_item_yn", "")));
        ObjectNode json = mapper.valueToTree(result);
        String expected = json.get(field).textValue();
        for (var status : KisStockTradingFlagStatus.values()) {
            ObjectNode changed = json.deepCopy().put(field, status.name());
            if (status.name().equals(expected)) {
                assertThat(mapper.treeToValue(changed, KisStockBasicInfoRestrictionObservationResult.class)).isEqualTo(result);
            } else {
                assertThatThrownBy(() -> mapper.treeToValue(changed, KisStockBasicInfoRestrictionObservationResult.class))
                        .hasRootCauseInstanceOf(IllegalArgumentException.class);
            }
        }
        ObjectNode removed = json.deepCopy().putNull(field);
        assertThatThrownBy(() -> mapper.treeToValue(removed, KisStockBasicInfoRestrictionObservationResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(KisStockBasicInfoTypeResolutionReasonCode.class)
    void preservesCompleteInputsAndFlagsAcrossJsonRoundTrip(KisStockBasicInfoTypeResolutionReasonCode reason) throws Exception {
        var input = KisStockBasicInfoTypeResolutionFixture.sample(reason);
        var result = policy.evaluate(input);
        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoRestrictionObservationResult.class);
        assertThat(restored).isEqualTo(result);
        assertThat(restored.typeResolution()).isEqualTo(input);
        assertThat(statuses(restored)).isEqualTo(statuses(result));
    }

    @Test
    void preservesAbsentObservationStagesForMissingRequestAcrossJsonRoundTrip() throws Exception {
        var input = KisStockBasicInfoTypeResolutionFixture.policy().resolve(
                KisStockBasicInfoTypeResolutionFixture.matching("999999", "KR7005930003", "STK", "ST", "101"));
        var result = policy.evaluate(input);
        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoRestrictionObservationResult.class);
        assertThat(restored).isEqualTo(result);
        assertThat(statuses(restored)).containsOnlyNulls();
    }

    @ParameterizedTest
    @MethodSource("fields")
    void rejectsInjectedStagesEvenWhenTheyClaimAFieldWasNotProvided(String field) {
        var input = KisStockBasicInfoTypeResolutionFixture.policy().resolve(
                KisStockBasicInfoTypeResolutionFixture.matching("999999", "KR7005930003", "STK", "ST", "101"));
        ObjectNode json = mapper.valueToTree(policy.evaluate(input));
        for (var status : KisStockTradingFlagStatus.values()) {
            var changed = json.deepCopy().put(field, status.name());
            assertThatThrownBy(() -> mapper.treeToValue(changed, KisStockBasicInfoRestrictionObservationResult.class))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"basicInfoSuspensionStatus", "basicInfoManagementStatus"})
    void rejectsApiStageInjectionForFailedComparisonWithAnExistingMaster(String field) {
        var input = KisStockBasicInfoTypeResolutionFixture.sample(KisStockBasicInfoTypeResolutionReasonCode.MATCH_NOT_CONFIRMED);
        var result = policy.evaluate(input);
        ObjectNode json = mapper.valueToTree(result);
        assertThat(result.masterSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        var changed = json.put(field, "N_OBSERVED");
        assertThatThrownBy(() -> mapper.treeToValue(changed, KisStockBasicInfoRestrictionObservationResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFlagsForADifferentSourceInput() {
        var result = policy.evaluate(input(KOSPI, Map.of("suspension", "Y"), Map.of("tr_stop_yn", "Y")));
        ObjectNode json = mapper.valueToTree(result);
        json.set("typeResolution", mapper.valueToTree(input(KOSPI, Map.of(), Map.of())));
        assertThatThrownBy(() -> mapper.treeToValue(json, KisStockBasicInfoRestrictionObservationResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionObservationResult(null,
                null, null, null, null, null, null, null, KisStockBasicInfoRestrictionObservationPolicy.OBSERVATION_VERSION))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankObservationVersion(String version) {
        var result = policy.evaluate(input(KOSPI, Map.of(), Map.of()));
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionObservationResult(result.typeResolution(),
                result.masterSuspensionStatus(), result.masterLiquidationStatus(), result.masterSpacStatus(),
                result.masterManagementStatus(), result.masterInvestmentCautionStatus(), result.basicInfoSuspensionStatus(),
                result.basicInfoManagementStatus(), version)).isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> fields() {
        return RESULT_FIELDS.stream();
    }
}
