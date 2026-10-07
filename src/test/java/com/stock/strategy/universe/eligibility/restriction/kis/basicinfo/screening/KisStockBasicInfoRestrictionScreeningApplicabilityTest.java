package com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION_V1;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.REVIEW_REQUIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.support.KisStockBasicInfoRestrictionObservationFixture.input;
import static com.stock.strategy.universe.eligibility.restriction.kis.result.KisStockTradingFlagStatus.FIELD_NOT_PROVIDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoRestrictionScreeningApplicabilityTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final KisStockBasicInfoRestrictionObservationPolicy observationPolicy = new KisStockBasicInfoRestrictionObservationPolicy();
    private final KisStockBasicInfoRestrictionScreeningPolicy policy = new KisStockBasicInfoRestrictionScreeningPolicy();

    @Test
    void distinguishesNonApplicabilityFromMissingRawDataWithoutApprovingOtherEvidence() throws Exception {
        var observation = kospi(Map.of(), Map.of("nxt_tr_stop_yn", "Y", "lstg_abol_dt", "UNKNOWN_DATE"));
        var result = policy.evaluate(observation);
        assertThat(result.status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED,
                MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE);
        assertThat(result.observation()).isSameAs(observation);
        assertThat(observation.masterInvestmentCautionStatus()).isEqualTo(FIELD_NOT_PROVIDED);
        assertThat(observation.typeResolution().masterClassification().rawRecord().rawInvestmentCaution()).isNull();
        assertThat(observation.typeResolution().matchingResult().apiInput().rawRecord().rawNxtSuspension()).isEqualTo("Y");
        assertThat(observation.typeResolution().matchingResult().apiInput().rawRecord().rawDelistingDate()).isEqualTo("UNKNOWN_DATE");
        assertThat(mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoRestrictionScreeningResult.class)).isEqualTo(result);
        assertThat(policy.evaluate(observation, SCREENING_VERSION_V1).status()).isEqualTo(REVIEW_REQUIRED);
    }

    @ParameterizedTest
    @CsvSource({"suspension,MASTER_SUSPENSION", "liquidation,MASTER_LIQUIDATION", "spac,MASTER_SPAC",
            "management,MASTER_MANAGEMENT", "tr_stop_yn,BASIC_INFO_SUSPENSION", "admn_item_yn,BASIC_INFO_MANAGEMENT"})
    void nonApplicabilityNeverHidesAnotherPositiveOrUnverifiedField(String field, String prefix) {
        for (String raw : List.of("Y", "?", " ", "n")) {
            boolean api = field.endsWith("_yn");
            var result = policy.evaluate(kospi(api ? Map.of() : Map.of(field, raw), api ? Map.of(field, raw) : Map.of()));
            var reason = KisStockBasicInfoRestrictionScreeningReasonCode.valueOf(prefix
                    + (raw.equals("Y") ? "_Y_OBSERVED" : "_VALUE_UNVERIFIED"));
            assertThat(result.status()).isEqualTo(raw.equals("Y") ? EXCLUSION_SIGNAL_OBSERVED : REVIEW_REQUIRED);
            assertThat(result.reasonCodes()).contains(reason, MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED,
                    MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE).hasSize(3);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"N|NO_EXCLUSION_SIGNAL_OBSERVED", "Y|EXCLUSION_SIGNAL_OBSERVED", "' '|REVIEW_REQUIRED",
            "?|REVIEW_REQUIRED", "n|REVIEW_REQUIRED"}, delimiter = '|')
    void kosdaqNeverUsesTheKospiExemption(String raw, String status) {
        var observation = observationPolicy.evaluate(input(KOSDAQ, Map.of("caution", raw), Map.of()));
        var v1 = policy.evaluate(observation, SCREENING_VERSION_V1);
        var v2 = policy.evaluate(observation);
        assertThat(v2.status().name()).isEqualTo(status);
        assertThat(v2.status()).isEqualTo(v1.status());
        assertThat(v2.reasonCodes()).isEqualTo(v1.reasonCodes()).doesNotContain(MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"201", "202", "000", "?"})
    void preferredOrUnverifiedTypesStayOutsideTheVerifiedScope(String kind) {
        var result = policy.evaluate(kospi(Map.of(), Map.of("stck_kind_cd", kind)));
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MASTER_TYPE_ONLY", "TYPE_CONFLICT"})
    void etfAndConflictingTypesDoNotReceiveAnExemption(String reason) {
        var observation = observationPolicy.evaluate(KisStockBasicInfoTypeResolutionFixture.sample(
                KisStockBasicInfoTypeResolutionReasonCode.valueOf(reason)));
        var v1 = policy.evaluate(observation, SCREENING_VERSION_V1);
        var v2 = policy.evaluate(observation);
        assertThat(v2.status()).isEqualTo(v1.status());
        assertThat(v2.reasonCodes()).isEqualTo(v1.reasonCodes()).doesNotContain(MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "observationVersion|KIS_STOCK_BASIC_INFO_RESTRICTION_OBSERVATION_V1",
            "resolutionVersion|KIS_STOCK_BASIC_INFO_CURRENT_TYPE_RESOLUTION_V1",
            "matchingVersion|KIS_STOCK_BASIC_INFO_STANDARD_CODE_MARKET_V1",
            "classificationVersion|KIS_STOCK_MASTER_CURRENT_TYPE_V1",
            "sourceRevision|277ec0eb7a9b7f63b6807829286c80f36649dad2",
            "parserVersion|KIS_STOCK_MASTER_RAW_V2",
            "layoutVersion|OBSERVED_2026_10_05_LF_V1",
            "classificationVersion|KIS_STOCK_BASIC_INFO_CURRENT_TYPE_V1",
            "sourceReference|build/kis-stock-eligibility-source-validation-01/apiportal-specification.json",
            "sourceSha256|1fbf349c755a3f54469450e0b7ca89ee3aa3a779e4a33286348cc2e198c0567a",
            "parserVersion|KIS_STOCK_BASIC_INFO_RAW_V1"
    }, delimiter = '|')
    void unverifiedProvenanceStaysReviewRequiredAndCannotRetainAV2Exemption(String field, String previous) throws Exception {
        var original = policy.evaluate(kospi(Map.of(), Map.of()));
        ObjectNode changed = mapper.valueToTree(original.observation());
        String replacement = field.equals("sourceRevision") ? "0".repeat(40)
                : field.equals("sourceSha256") ? "0".repeat(64) : "UNVERIFIED";
        assertThat(replaceField(changed, field, previous, replacement)).isPositive();
        var observation = mapper.treeToValue(changed, KisStockBasicInfoRestrictionObservationResult.class);
        var result = policy.evaluate(observation);
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED);
        ObjectNode forged = mapper.valueToTree(original);
        forged.set("observation", changed);
        assertThatThrownBy(() -> mapper.treeToValue(forged, KisStockBasicInfoRestrictionScreeningResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explanationCannotBeRemovedOrInjectedIntoKosdaqAndUnknownContext() {
        var valid = policy.evaluate(kospi(Map.of(), Map.of()));
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(valid.observation(), valid.status(),
                List.of(MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE), valid.screeningVersion())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(valid.observation(), valid.status(),
                List.of(MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED), valid.screeningVersion())).isInstanceOf(IllegalArgumentException.class);
        for (var observation : List.of(observationPolicy.evaluate(input(KOSDAQ, Map.of(), Map.of())),
                kospi(Map.of(), Map.of("stck_kind_cd", "?")))) {
            var result = policy.evaluate(observation);
            var reasons = new ArrayList<>(result.reasonCodes());
            reasons.add(MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE);
            assertThatThrownBy(() -> new KisStockBasicInfoRestrictionScreeningResult(observation,
                    NO_EXCLUSION_SIGNAL_OBSERVED, reasons, result.screeningVersion())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private KisStockBasicInfoRestrictionObservationResult kospi(Map<String, String> master, Map<String, String> api) {
        return observationPolicy.evaluate(input(KOSPI, master, api));
    }

    private static int replaceField(JsonNode node, String field, String previous, String replacement) {
        int count = 0;
        if (node instanceof ObjectNode object && object.has(field) && object.get(field).asText().equals(previous)) {
            object.put(field, replacement);
            count++;
        }
        for (var child : node) {
            count += replaceField(child, field, previous, replacement);
        }
        return count;
    }
}
