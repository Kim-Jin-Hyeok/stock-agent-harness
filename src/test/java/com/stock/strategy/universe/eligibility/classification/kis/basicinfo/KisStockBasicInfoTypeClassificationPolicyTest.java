package com.stock.strategy.universe.eligibility.classification.kis.basicinfo;

import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.result.KisStockBasicInfoTypeClassificationReasonCode;
import com.stock.strategy.universe.eligibility.input.StockSecurityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.support.KisStockBasicInfoTypeClassificationFixture.fields;
import static com.stock.strategy.universe.eligibility.classification.kis.basicinfo.support.KisStockBasicInfoTypeClassificationFixture.parsed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoTypeClassificationPolicyTest {
    private final KisStockBasicInfoTypeClassificationPolicy policy = new KisStockBasicInfoTypeClassificationPolicy();

    @ParameterizedTest
    @CsvSource({"STK,101,COMMON_STOCK", "KSQ,101,COMMON_STOCK",
            "STK,201,PREFERRED_STOCK", "KSQ,201,PREFERRED_STOCK",
            "STK,202,PREFERRED_STOCK", "KSQ,202,PREFERRED_STOCK"})
    void interpretsOnlySupportedCombinationsWithFixedSpecificationEvidence(String market, String kind, StockSecurityType type) {
        var input = parsed(market, "300", "ST", kind);

        var result = policy.classify(input);

        assertThat(result.securityType()).isEqualTo(type);
        assertThat(result.reasonCode()).isEqualTo(KisStockBasicInfoTypeClassificationReasonCode.TYPE_INTERPRETED);
        assertThat(result.parseResult()).isSameAs(input);
        assertThat(result.classificationVersion()).isEqualTo("KIS_STOCK_BASIC_INFO_CURRENT_TYPE_V1");
        assertThat(result.sourceReference()).isEqualTo("build/kis-stock-eligibility-source-validation-01/apiportal-specification.json");
        assertThat(result.sourceSha256()).isEqualTo("1fbf349c755a3f54469450e0b7ca89ee3aa3a779e4a33286348cc2e198c0567a");
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "stk", "ksq", " STK", "KSQ ", "KOSPI", "KOSDAQ", "J", "NX", "UN", "UNKNOWN"})
    void keepsUnknownNonexactAndOtherSourcesMarketCodesUnverified(String market) {
        assertReason(parsed(market, "300", "ST", "101"), KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AGR", "BON", "CMD", "CUR", "ENG", "EQU", "ETF", "IRT", "KNX", "MTL", "SPI"})
    void distinguishesDefinedUnsupportedMarketsFromUnknownValues(String market) {
        assertReason(parsed(market, "300", "ST", "101"), KisStockBasicInfoTypeClassificationReasonCode.MARKET_UNSUPPORTED);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "300 ", " 300", "0300", "301", "302", "306", "UNKNOWN"})
    void doesNotInferUnobservedResponseProductTypesFromRequestParameterDefinitions(String productType) {
        assertReason(parsed("STK", productType, "ST", "101"),
                KisStockBasicInfoTypeClassificationReasonCode.PRODUCT_TYPE_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "st", "ST ", " ST", "COMMON_STOCK", "ETF", "ETN", "UNKNOWN"})
    void keepsUnknownAndNonexactSecurityGroupsUnverified(String group) {
        assertReason(parsed("KSQ", "300", group, "101"),
                KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BC", "DR", "EF", "EN", "EW", "FE", "FO", "FS", "FU", "FX", "GD", "IC", "IF", "KN",
            "MF", "OP", "RT", "SC", "SR", "SW", "TC"})
    void doesNotPromoteOtherDefinedGroupsEvenWhenKindIsCommon(String group) {
        for (String market : new String[]{"STK", "KSQ"}) {
            assertReason(parsed(market, "300", group, "101"),
                    KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
        }
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {" ", "101 ", " 101", "0101", "1", "100", "200", "221", "999", "COMMON_STOCK", "UNKNOWN"})
    void preservesBlankUnknownAndNonexactKindsInsteadOfSupplyingAType(String kind) {
        assertReason(parsed("STK", "300", "ST", kind),
                KisStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"000", "203", "204", "205", "206", "207", "208", "209", "210", "211", "212", "213",
            "214", "215", "216", "217", "218", "219", "220", "301", "401"})
    void defersDefinedButUnsupportedKindsIncludingRemainingPreferredCodes(String kind) {
        for (String market : new String[]{"STK", "KSQ"}) {
            assertReason(parsed(market, "300", "ST", kind),
                    KisStockBasicInfoTypeClassificationReasonCode.TYPE_COMBINATION_UNSUPPORTED);
        }
    }

    @Test
    void retainsFirstFailureReasonWithoutDiscardingTheOtherRawValues() {
        assertReason(parsed("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN"),
                KisStockBasicInfoTypeClassificationReasonCode.MARKET_VALUE_UNVERIFIED);
        assertReason(parsed("KNX", "UNKNOWN", "UNKNOWN", "UNKNOWN"),
                KisStockBasicInfoTypeClassificationReasonCode.MARKET_UNSUPPORTED);
        assertReason(parsed("STK", "UNKNOWN", "UNKNOWN", "UNKNOWN"),
                KisStockBasicInfoTypeClassificationReasonCode.PRODUCT_TYPE_VALUE_UNVERIFIED);
        assertReason(parsed("KSQ", "300", "UNKNOWN", "UNKNOWN"),
                KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_VALUE_UNVERIFIED);
        assertReason(parsed("STK", "300", "EF", ""),
                KisStockBasicInfoTypeClassificationReasonCode.SECURITY_GROUP_UNSUPPORTED);
    }

    @ParameterizedTest
    @MethodSource("nonTypeRawValues")
    void preservesAllOtherRawFieldsWithoutCertifyingIdentityRestrictionsOrListing(String field, String value) {
        var input = parsed(fields("STK", "300", "ST", "101").put(field, value));

        var result = policy.classify(input);

        assertThat(result.securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.parseResult()).isSameAs(input);
        assertThat(result.parseResult().rawRecord()).isSameAs(input.rawRecord());
        assertThat(result.parseResult().inputSha256()).isEqualTo(input.inputSha256());
        assertThat(result.parseResult().parserVersion()).isEqualTo(input.parserVersion());
        assertThat(result.parseResult().message()).isEqualTo(input.message());
    }

    @Test
    void retainsSpacAndSuspendedManagedStockObservationsWithoutAnEligibilityApproval() {
        var input = parsed(fields("KSQ", "300", "ST", "101")
                .put("pdno", "00000A0004Y0").put("prdt_name", "SYNTHETIC SPAC")
                .put("tr_stop_yn", "Y").put("admn_item_yn", "Y").put("lstg_abol_dt", "")
                .put("nxt_tr_stop_yn", "N").put("cptt_trad_tr_psbl_yn", "N"));

        var result = policy.classify(input);

        assertThat(result.securityType()).isEqualTo(StockSecurityType.COMMON_STOCK);
        assertThat(result.parseResult()).isSameAs(input);
        assertThat(result.parseResult().rawRecord().productNumber()).isEqualTo("00000A0004Y0");
        assertThat(result.parseResult().rawRecord().rawSuspension()).isEqualTo("Y");
        assertThat(result.parseResult().rawRecord().rawManagement()).isEqualTo("Y");
        assertThat(result.parseResult().rawRecord().rawDelistingDate()).isEmpty();
        assertThat(result.parseResult().rawRecord().rawNxtSuspension()).isEqualTo("N");
        assertThat(result.parseResult().rawRecord().rawCompetitiveTradingPermission()).isEqualTo("N");
    }

    @Test
    void doesNotInferATypeFromNamesWhenKindIsBlank() {
        var input = parsed(fields("STK", "300", "ST", "").put("prdt_name", "COMMON_STOCK PREFERRED_STOCK"));

        assertReason(input, KisStockBasicInfoTypeClassificationReasonCode.STOCK_KIND_VALUE_UNVERIFIED);
    }

    @Test
    void doesNotRetainResultsAcrossCallsOrPolicyInstances() {
        var common = parsed("STK", "300", "ST", "101");
        var first = policy.classify(common);

        assertThat(policy.classify(parsed("KSQ", "300", "ST", "202")).securityType()).isEqualTo(StockSecurityType.PREFERRED_STOCK);
        assertThat(policy.classify(parsed("STK", "300", "ST", "")).securityType()).isNull();
        assertThat(policy.classify(common)).isEqualTo(first);
        assertThat(new KisStockBasicInfoTypeClassificationPolicy().classify(common)).isEqualTo(first);
    }

    @Test
    void rejectsNullParseResult() {
        assertThatThrownBy(() -> policy.classify(null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("parseResult");
    }

    private void assertReason(KisStockBasicInfoParseResult input, KisStockBasicInfoTypeClassificationReasonCode reason) {
        var result = policy.classify(input);

        assertThat(result.securityType()).isNull();
        assertThat(result.reasonCode()).isEqualTo(reason);
        assertThat(result.parseResult()).isSameAs(input);
        assertThat(result.classificationVersion()).isEqualTo(KisStockBasicInfoTypeClassificationPolicy.CLASSIFICATION_VERSION);
        assertThat(result.sourceReference()).isEqualTo(KisStockBasicInfoTypeClassificationPolicy.SOURCE_REFERENCE);
        assertThat(result.sourceSha256()).isEqualTo(KisStockBasicInfoTypeClassificationPolicy.SOURCE_SHA256);
    }

    private static Stream<Arguments> nonTypeRawValues() {
        return List.of("pdno", "std_pdno", "prdt_name", "tr_stop_yn", "admn_item_yn", "scts_mket_lstg_dt",
                        "scts_mket_lstg_abol_dt", "kosdaq_mket_lstg_dt", "kosdaq_mket_lstg_abol_dt", "lstg_abol_dt",
                        "nxt_tr_stop_yn", "cptt_trad_tr_psbl_yn").stream()
                .flatMap(field -> Stream.of("", " \t ", "NOT_A_DATE_OR_CODE")
                        .map(value -> Arguments.of(field, value)));
    }
}
