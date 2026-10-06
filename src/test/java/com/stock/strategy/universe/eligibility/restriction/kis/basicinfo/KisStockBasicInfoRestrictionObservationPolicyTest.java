package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo;

import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.MASTER_FIELDS;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.expected;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.input;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.statuses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRestrictionObservationPolicyTest {
    private final KisStockBasicInfoRestrictionObservationPolicy policy = new KisStockBasicInfoRestrictionObservationPolicy();

    @ParameterizedTest
    @MethodSource("masterValues")
    void observesEachMasterFieldWithoutNormalizingOtherFields(KisStockMasterMarket market, String field, String value) {
        var input = input(market, Map.of(field, value), Map.of());
        var result = policy.evaluate(input);
        var observed = statuses(result);
        for (int index = 0; index < MASTER_FIELDS.size(); index++) {
            String current = MASTER_FIELDS.get(index);
            String raw = current.equals("caution") && market == KOSPI ? null : current.equals(field) ? value : "N";
            assertThat(observed.get(index)).isEqualTo(expected(raw));
        }
        assertThat(result.basicInfoSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.basicInfoManagementStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.typeResolution()).isSameAs(input);
    }

    @ParameterizedTest
    @MethodSource("apiValues")
    void preservesApiLiteralValuesWithoutDefaultsOrCrossSourceOverwrite(KisStockMasterMarket market, String field, String value) {
        var input = input(market, Map.of("suspension", "Y", "management", "Y"), Map.of(field, value));
        var result = policy.evaluate(input);
        assertThat(result.basicInfoSuspensionStatus()).isEqualTo(expected(field.equals("tr_stop_yn") ? value : "N"));
        assertThat(result.basicInfoManagementStatus()).isEqualTo(expected(field.equals("admn_item_yn") ? value : "N"));
        assertThat(result.masterSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.masterManagementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        var raw = input.basicInfoClassification().parseResult().rawRecord();
        assertThat(field.equals("tr_stop_yn") ? raw.rawSuspension() : raw.rawManagement()).isEqualTo(value);
        assertThat(result.typeResolution()).isSameAs(input);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void retainsAllPositiveMasterObservationsAndContrastingApiObservations(KisStockMasterMarket market) {
        var flags = new HashMap<String, String>();
        MASTER_FIELDS.stream().filter(field -> market == KOSDAQ || !field.equals("caution")).forEach(field -> flags.put(field, "Y"));
        var input = input(market, flags, Map.of("tr_stop_yn", "N", "admn_item_yn", "N"));
        var result = policy.evaluate(input);
        assertThat(result.masterSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.masterLiquidationStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.masterSpacStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.masterManagementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.masterInvestmentCautionStatus()).isEqualTo(market == KOSPI
                ? KisStockTradingFlagStatus.FIELD_NOT_PROVIDED : KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.basicInfoSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.basicInfoManagementStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.typeResolution().referenceSecurityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
    }

    @ParameterizedTest
    @CsvSource(value = {"999999|KR7005930003|STK|REQUESTED_SYMBOL_NOT_FOUND",
            "005930|''|STK|API_STANDARD_CODE_BLANK", "005930|KR7111111111|STK|STANDARD_CODE_MISMATCH",
            "005930|KR7005930003|UNKNOWN|API_MARKET_UNVERIFIED", "005930|KR7005930003|KSQ|MARKET_MISMATCH"}, delimiter = '|', emptyValue = "")
    void neverLinksApiFlagsAfterAnyFailedComparison(String symbol, String standardCode, String market, KisStockBasicInfoMatchReasonCode reason) {
        var input = KisStockBasicInfoTypeResolutionFixture.policy().resolve(
                KisStockBasicInfoTypeResolutionFixture.matching(symbol, standardCode, market, "ST", "101"));
        var result = policy.evaluate(input);
        assertThat(input.matchingResult().reasonCode()).isEqualTo(reason);
        assertThat(result.basicInfoSuspensionStatus()).isNull();
        assertThat(result.basicInfoManagementStatus()).isNull();
        assertThat(result.typeResolution().matchingResult().apiInput()).isSameAs(input.matchingResult().apiInput());
        if (input.masterClassification() == null) {
            assertThat(statuses(result)).containsOnlyNulls();
        } else {
            assertThat(result.masterSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
            assertThat(result.masterManagementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
            assertThat(result.masterInvestmentCautionStatus()).isEqualTo(KisStockTradingFlagStatus.FIELD_NOT_PROVIDED);
        }
    }

    @ParameterizedTest
    @EnumSource(value = KisStockBasicInfoTypeResolutionReasonCode.class, names = {"TYPE_CONFLICT", "TYPE_UNVERIFIED", "MASTER_TYPE_ONLY"})
    void observesMatchedApiFlagsEvenWhenTheApiTypeIsNullOrTypesConflict(KisStockBasicInfoTypeResolutionReasonCode reason) {
        var input = KisStockBasicInfoTypeResolutionFixture.sample(reason);
        var result = policy.evaluate(input);
        assertThat(result.basicInfoSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.basicInfoManagementStatus()).isEqualTo(KisStockTradingFlagStatus.Y_OBSERVED);
        assertThat(result.typeResolution()).isSameAs(input);
        assertThat(result.typeResolution().reasonCode()).isEqualTo(reason);
    }

    @Test
    void distinguishesAbsentKospiCautionFromBlankAndNegativeKosdaqFields() {
        assertThat(policy.evaluate(input(KOSPI, Map.of(), Map.of())).masterInvestmentCautionStatus())
                .isEqualTo(KisStockTradingFlagStatus.FIELD_NOT_PROVIDED);
        assertThat(policy.evaluate(input(KOSDAQ, Map.of("caution", " "), Map.of())).masterInvestmentCautionStatus())
                .isEqualTo(KisStockTradingFlagStatus.VALUE_UNVERIFIED);
        assertThat(policy.evaluate(input(KOSDAQ, Map.of(), Map.of())).masterInvestmentCautionStatus())
                .isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
    }

    @Test
    void preservesExcludedNxtDateAndProductFieldsWithoutInterpretingThem() {
        var input = input(KOSPI, Map.of(), Map.of("nxt_tr_stop_yn", "Y", "cptt_trad_tr_psbl_yn", "Y", "lstg_abol_dt", "UNKNOWN_DATE"));
        var result = policy.evaluate(input);
        var raw = result.typeResolution().matchingResult().apiInput().rawRecord();
        assertThat(raw.rawNxtSuspension()).isEqualTo("Y");
        assertThat(raw.rawCompetitiveTradingPermission()).isEqualTo("Y");
        assertThat(raw.rawDelistingDate()).isEqualTo("UNKNOWN_DATE");
        assertThat(raw.productNumber()).isEqualTo("00000A005930");
        assertThat(result.basicInfoSuspensionStatus()).isEqualTo(KisStockTradingFlagStatus.N_OBSERVED);
        assertThat(result.observationVersion()).isEqualTo("KIS_STOCK_BASIC_INFO_RESTRICTION_OBSERVATION_V1");
    }

    @Test
    void neverRetainsPreviousRequestsObservationsAndRejectsNullInput() {
        var input = input(KOSPI, Map.of(), Map.of());
        var first = policy.evaluate(input);
        policy.evaluate(input(KOSDAQ, Map.of("spac", "Y"), Map.of("tr_stop_yn", "Y")));
        assertThat(policy.evaluate(input)).isEqualTo(first);
        assertThatThrownBy(() -> policy.evaluate(null)).isInstanceOf(NullPointerException.class);
    }

    static Stream<Arguments> masterValues() {
        return Stream.of(KisStockMasterMarket.values()).flatMap(market -> MASTER_FIELDS.stream()
                .filter(field -> market == KOSDAQ || !field.equals("caution"))
                .flatMap(field -> Stream.of("Y", "N", " ", "?", "y", "n").map(value -> Arguments.of(market, field, value))));
    }

    static Stream<Arguments> apiValues() {
        return Stream.of(KisStockMasterMarket.values()).flatMap(market -> Stream.of("tr_stop_yn", "admn_item_yn")
                .flatMap(field -> Stream.of("Y", "N", "", " ", "y", "n", " Y", "Y ", "YES", "0", "1", "?")
                        .map(value -> Arguments.of(market, field, value))));
    }
}
