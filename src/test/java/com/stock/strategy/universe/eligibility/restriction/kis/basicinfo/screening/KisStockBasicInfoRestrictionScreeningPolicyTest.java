package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.input;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_MANAGEMENT_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_SUSPENSION_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_LIQUIDATION_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_MANAGEMENT_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_SPAC_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_SUSPENSION_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRestrictionScreeningPolicyTest {
    private static final List<String> FIELDS = List.of("suspension", "liquidation", "spac", "management", "caution",
            "tr_stop_yn", "admn_item_yn");
    private static final List<String> REASON_PREFIXES = List.of("MASTER_SUSPENSION", "MASTER_LIQUIDATION", "MASTER_SPAC",
            "MASTER_MANAGEMENT", "MASTER_INVESTMENT_CAUTION", "BASIC_INFO_SUSPENSION", "BASIC_INFO_MANAGEMENT");
    private final KisStockBasicInfoRestrictionObservationPolicy observationPolicy = new KisStockBasicInfoRestrictionObservationPolicy();
    private final KisStockBasicInfoRestrictionScreeningPolicy policy = new KisStockBasicInfoRestrictionScreeningPolicy();

    @Test
    void reportsNoSignalOnlyWhenAllSevenMatchedObservationsAreN() {
        var observation = observation(Map.of(), Map.of());
        var result = policy.evaluate(observation);
        assertThat(result.status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).isEmpty();
        assertThat(result.observation()).isSameAs(observation);
        assertThat(result.screeningVersion()).isEqualTo("KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V1");
    }

    @ParameterizedTest
    @MethodSource("positiveFields")
    void retainsTheSpecificSourceAndFieldForEachPositiveObservation(int index) {
        var observation = changedField(index, "Y");
        var result = policy.evaluate(observation);
        assertThat(result.status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).containsExactly(reason(index, "Y_OBSERVED"));
        assertThat(result.observation()).isSameAs(observation);
    }

    @ParameterizedTest
    @MethodSource("unverifiedValues")
    void requiresReviewWithoutNormalizingBlankLowercaseOrUndefinedValues(int index, String value) {
        var observation = changedField(index, value);
        var result = policy.evaluate(observation);
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reasonCodes()).containsExactly(reason(index, "VALUE_UNVERIFIED"));
        assertThat(result.observation()).isSameAs(observation);
    }

    @Test
    void preservesEveryCombinationOfPositiveFieldsInStableSourceAndFieldOrder() {
        for (int mask = 0; mask < 1 << FIELDS.size(); mask++) {
            var master = new HashMap<String, String>();
            var api = new HashMap<String, String>();
            var expected = new ArrayList<KisStockBasicInfoRestrictionScreeningReasonCode>();
            for (int index = 0; index < FIELDS.size(); index++) {
                if ((mask & 1 << index) != 0) {
                    (index < 5 ? master : api).put(FIELDS.get(index), "Y");
                    expected.add(reason(index, "Y_OBSERVED"));
                }
            }
            var result = policy.evaluate(observation(master, api));
            assertThat(result.status()).as("mask=%s", mask).isEqualTo(mask == 0
                    ? NO_EXCLUSION_SIGNAL_OBSERVED : EXCLUSION_SIGNAL_OBSERVED);
            assertThat(result.reasonCodes()).as("mask=%s", mask).containsExactlyElementsOf(expected);
        }
    }

    @Test
    void keepsUnverifiedReasonsEvenWhenPositiveSignalsTakePriority() {
        var result = policy.evaluate(observation(Map.of("liquidation", "Y", "caution", "?"), Map.of("tr_stop_yn", "Y")));
        assertThat(result.status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_LIQUIDATION_Y_OBSERVED,
                MASTER_INVESTMENT_CAUTION_VALUE_UNVERIFIED, BASIC_INFO_SUSPENSION_Y_OBSERVED);
    }

    @Test
    void neverUsesApiNToOverwriteMasterYOrMasterNToOverwriteApiY() {
        var masterPositive = observation(Map.of("suspension", "Y", "management", "Y"), Map.of());
        var apiPositive = observation(Map.of(), Map.of("tr_stop_yn", "Y", "admn_item_yn", "Y"));
        assertThat(policy.evaluate(masterPositive).reasonCodes()).containsExactly(
                MASTER_SUSPENSION_Y_OBSERVED, MASTER_MANAGEMENT_Y_OBSERVED);
        assertThat(policy.evaluate(apiPositive).reasonCodes()).containsExactly(
                BASIC_INFO_SUSPENSION_Y_OBSERVED, BASIC_INFO_MANAGEMENT_Y_OBSERVED);
        assertThat(policy.evaluate(masterPositive).observation()).isSameAs(masterPositive);
        assertThat(policy.evaluate(apiPositive).observation()).isSameAs(apiPositive);
    }

    @Test
    void treatsKospiCautionAsMissingEvidenceNotAnImplicitNegative() {
        var clean = observationPolicy.evaluate(input(KOSPI, Map.of(), Map.of()));
        var positive = observationPolicy.evaluate(input(KOSPI, Map.of("spac", "Y"), Map.of()));
        assertThat(policy.evaluate(clean).status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(policy.evaluate(clean).reasonCodes()).containsExactly(MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED);
        assertThat(policy.evaluate(positive).status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(policy.evaluate(positive).reasonCodes()).containsExactly(MASTER_SPAC_Y_OBSERVED,
                MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED);
    }

    @ParameterizedTest
    @CsvSource(value = {"999999|KR7005930003|STK|REQUESTED_SYMBOL_NOT_FOUND",
            "005930|''|STK|API_STANDARD_CODE_BLANK", "005930|KR7111111111|STK|STANDARD_CODE_MISMATCH",
            "005930|KR7005930003|UNKNOWN|API_MARKET_UNVERIFIED", "005930|KR7005930003|KSQ|MARKET_MISMATCH"}, delimiter = '|', emptyValue = "")
    void prioritizesReviewForEveryFailedMatchAndKeepsMasterDiagnosticsOnly(
            String symbol, String standardCode, String market, KisStockBasicInfoMatchReasonCode reason
    ) {
        var type = KisStockBasicInfoTypeResolutionFixture.policy().resolve(
                KisStockBasicInfoTypeResolutionFixture.matching(symbol, standardCode, market, "ST", "101"));
        var observation = observationPolicy.evaluate(type);
        var result = policy.evaluate(observation);
        assertThat(type.matchingResult().reasonCode()).isEqualTo(reason);
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.observation()).isSameAs(observation);
        assertThat(observation.basicInfoSuspensionStatus()).isNull();
        assertThat(observation.basicInfoManagementStatus()).isNull();
        if (type.masterClassification() == null) {
            assertThat(result.reasonCodes()).containsExactly(STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED);
        } else {
            assertThat(result.reasonCodes()).containsExactly(STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED,
                    MASTER_SUSPENSION_Y_OBSERVED, MASTER_LIQUIDATION_Y_OBSERVED, MASTER_SPAC_Y_OBSERVED,
                    MASTER_MANAGEMENT_Y_OBSERVED, MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED);
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockBasicInfoTypeResolutionReasonCode.class)
    void neverChangesTypeResolutionReasonsOrReferenceTypes(KisStockBasicInfoTypeResolutionReasonCode reason) {
        var type = KisStockBasicInfoTypeResolutionFixture.sample(reason);
        var observation = observationPolicy.evaluate(type);
        var result = policy.evaluate(observation);
        assertThat(result.observation().typeResolution()).isSameAs(type);
        assertThat(result.observation().typeResolution().reasonCode()).isEqualTo(reason);
    }

    @Test
    void noRestrictionSignalDoesNotResolveAnUnknownTypeOrNxtAndDateEvidence() {
        var observation = observation(Map.of(), Map.of("scty_grp_id_cd", "?", "stck_kind_cd", "?",
                "nxt_tr_stop_yn", "Y", "cptt_trad_tr_psbl_yn", "?", "lstg_abol_dt", "UNKNOWN_DATE"));
        var result = policy.evaluate(observation);
        assertThat(result.status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.observation().typeResolution().referenceSecurityType()).isNull();
        assertThat(result.observation().typeResolution().reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.TYPE_UNVERIFIED);
        assertThat(result.observation().typeResolution().matchingResult().apiInput().rawRecord().rawNxtSuspension()).isEqualTo("Y");
        assertThat(result.observation().typeResolution().matchingResult().apiInput().rawRecord().rawDelistingDate()).isEqualTo("UNKNOWN_DATE");
    }

    @Test
    void rejectsNullAndHasNoPreviousRequestState() {
        var observation = observation(Map.of(), Map.of());
        var first = policy.evaluate(observation);
        policy.evaluate(changedField(0, "Y"));
        policy.evaluate(observationPolicy.evaluate(input(KOSPI, Map.of(), Map.of())));
        assertThat(policy.evaluate(observation)).isEqualTo(first);
        assertThatThrownBy(() -> policy.evaluate(null)).isInstanceOf(NullPointerException.class);
    }

    private KisStockBasicInfoRestrictionObservationResult changedField(int index, String value) {
        return observation(index < 5 ? Map.of(FIELDS.get(index), value) : Map.of(),
                index < 5 ? Map.of() : Map.of(FIELDS.get(index), value));
    }

    private KisStockBasicInfoRestrictionObservationResult observation(Map<String, String> master, Map<String, String> api) {
        return observationPolicy.evaluate(input(KOSDAQ, master, api));
    }

    private static KisStockBasicInfoRestrictionScreeningReasonCode reason(int index, String suffix) {
        return KisStockBasicInfoRestrictionScreeningReasonCode.valueOf(REASON_PREFIXES.get(index) + "_" + suffix);
    }

    static Stream<Integer> positiveFields() {
        return Stream.iterate(0, index -> index + 1).limit(FIELDS.size());
    }

    static Stream<Arguments> unverifiedValues() {
        return positiveFields().flatMap(index -> (index < 5 ? Stream.of(" ", "?", "y", "n", "0", "1")
                : Stream.of("", " ", "?", "y", "n", "0", "1", " Y", "Y ", "YES"))
                .map(value -> Arguments.of(index, value)));
    }
}
