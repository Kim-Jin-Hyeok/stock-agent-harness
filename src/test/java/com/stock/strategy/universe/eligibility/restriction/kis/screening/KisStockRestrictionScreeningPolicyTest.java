package com.stock.strategy.universe.eligibility.restriction.kis.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.market.stock.master.provider.kis.parsing.KisStockMasterParser;
import com.stock.market.stock.master.provider.kis.parsing.warning.KisStockMasterMarketWarningParser;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.warning.KisStockMarketWarningObservationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.CP949;
import static com.stock.market.stock.master.provider.kis.parsing.support.KisStockMasterParsingFixture.content;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.basicInfo;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.batch;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.inputs;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.BASIC_INFO_REVIEW_REQUIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_INVESTMENT_WARNING_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionScreeningPolicyTest {
    private final KisStockRestrictionScreeningPolicy policy = new KisStockRestrictionScreeningPolicy();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void preservesBothInputsAndSelectsOnlyTheRequestedRecord(KisStockMasterMarket market) {
        var input = inputs(market, "00", "N", Map.of(), Map.of("pdno", "UNRELATED_PRODUCT"));
        var result = policy.evaluate(input.basicInfo(), input.warnings());
        assertThat(result.status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).isEmpty();
        assertThat(result.basicInfoScreening()).isSameAs(input.basicInfo());
        assertThat(result.marketWarningObservation()).isSameAs(input.warnings());
        assertThat(result.screeningVersion()).isEqualTo("KIS_STOCK_RESTRICTION_SCREENING_V1");
        assertThat(input.warnings().observations().get(1).rawRecord().rawMarketWarningCode()).isEqualTo("03");
        var matching = input.basicInfo().observation().typeResolution().matchingResult();
        var second = matching.masterBatch().marketResults().stream().filter(source -> source.market() == market).findFirst().orElseThrow().records().get(1);
        var secondBasicInfo = basicInfo(matching.masterBatch(), second.symbol(), second.standardCode(), market, Map.of());
        assertThat(policy.evaluate(secondBasicInfo, input.warnings()).status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        if (market == KOSPI) {
            assertThat(result.basicInfoScreening().reasonCodes()).extracting(Enum::name)
                    .containsExactly("MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED", "MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE");
        }
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void evaluatesAllCodeAndPreannouncementCombinationsIndependently(KisStockMasterMarket market) {
        var codes = new LinkedHashMap<String, String>();
        codes.put("00", null);
        codes.put("01", "MARKET_WARNING_INVESTMENT_CAUTION_OBSERVED");
        codes.put("02", "MARKET_WARNING_INVESTMENT_WARNING_OBSERVED");
        codes.put("03", "MARKET_WARNING_INVESTMENT_RISK_OBSERVED");
        for (String raw : new String[]{"  ", "0 ", " 0", "??", "ab", "99"}) {
            codes.put(raw, "MARKET_WARNING_VALUE_UNVERIFIED");
        }
        for (var code : codes.entrySet()) {
            for (String preannouncement : new String[]{"Y", "N", " ", "y", "n", "?", "0"}) {
                var input = inputs(market, code.getKey(), preannouncement, Map.of(), Map.of());
                var result = policy.evaluate(input.basicInfo(), input.warnings());
                var expected = new ArrayList<KisStockRestrictionScreeningReasonCode>();
                if (code.getValue() != null) {
                    expected.add(KisStockRestrictionScreeningReasonCode.valueOf(code.getValue()));
                }
                if (preannouncement.equals("Y")) {
                    expected.add(MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED);
                } else if (!preannouncement.equals("N")) {
                    expected.add(MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED);
                }
                boolean excluded = List.of("01", "02", "03").contains(code.getKey()) || preannouncement.equals("Y");
                assertThat(result.status()).isEqualTo(excluded ? EXCLUSION_SIGNAL_OBSERVED
                        : expected.isEmpty() ? NO_EXCLUSION_SIGNAL_OBSERVED : REVIEW_REQUIRED);
                assertThat(result.reasonCodes()).containsExactlyElementsOf(expected);
            }
        }
    }

    @Test
    void preservesExistingExclusionAndUnverifiedDiagnosticsWithoutOverwritingEitherSource() {
        var input = inputs(KOSDAQ, "??", "y", Map.of("suspension", "Y", "management", "?"), Map.of());
        var result = policy.evaluate(input.basicInfo(), input.warnings());
        assertThat(result.status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).containsExactly(BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED,
                MARKET_WARNING_VALUE_UNVERIFIED, MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED);
        assertThat(result.basicInfoScreening().reasonCodes()).extracting(Enum::name)
                .containsExactly("MASTER_SUSPENSION_Y_OBSERVED", "MASTER_MANAGEMENT_VALUE_UNVERIFIED");
        var review = inputs(KOSDAQ, "00", "N", Map.of("management", "?"), Map.of());
        assertThat(policy.evaluate(review.basicInfo(), review.warnings()).reasonCodes()).containsExactly(BASIC_INFO_REVIEW_REQUIRED);
        var warning = inputs(KOSDAQ, "02", "N", Map.of("management", "?"), Map.of());
        assertThat(policy.evaluate(warning.basicInfo(), warning.warnings()).reasonCodes())
                .containsExactly(BASIC_INFO_REVIEW_REQUIRED, MARKET_WARNING_INVESTMENT_WARNING_OBSERVED);
        assertThat(policy.evaluate(warning.basicInfo(), warning.warnings()).status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
    }

    @ParameterizedTest
    @CsvSource(value = {"999999|KR7005930003|STK", "005930|''|STK", "005930|KR7111111111|STK",
            "005930|KR7005930003|UNKNOWN", "005930|KR7005930003|KSQ"}, delimiter = '|', emptyValue = "")
    void givesEveryFailedIdentityMatchPriorityOverWarningExclusion(String symbol, String standardCode, String apiMarket) {
        var master = batch(KOSPI, "02", "Y", Map.of("suspension", "Y"));
        var basic = basicInfo(master, symbol, standardCode, KOSPI, Map.of("mket_id_cd", apiMarket));
        var result = policy.evaluate(basic, warnings(master, KOSPI));
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reasonCodes()).startsWith(STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED);
        assertThat(result.basicInfoScreening()).isSameAs(basic);
        assertThat(result.reasonCodes()).contains(BASIC_INFO_REVIEW_REQUIRED);
        if (!symbol.equals("999999")) {
            assertThat(result.reasonCodes()).contains(MARKET_WARNING_INVESTMENT_WARNING_OBSERVED, MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED);
        } else {
            assertThat(result.reasonCodes()).doesNotContain(MARKET_WARNING_INVESTMENT_WARNING_OBSERVED, MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED);
        }
    }

    @Test
    void refusesAnotherMarketOrAnotherMasterWithoutAttachingItsWarningSignals() {
        var input = inputs(KOSPI, "02", "Y", Map.of("suspension", "Y"), Map.of());
        var master = input.basicInfo().observation().typeResolution().matchingResult().masterBatch();
        var wrongMarket = policy.evaluate(input.basicInfo(), warnings(master, KOSDAQ));
        assertThat(wrongMarket.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(wrongMarket.reasonCodes()).containsExactly(KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MARKET_NOT_MATCHED,
                BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED);
        var different = inputs(KOSPI, "03", "Y", Map.of(), Map.of());
        var wrongSource = policy.evaluate(input.basicInfo(), different.warnings());
        assertThat(wrongSource.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(wrongSource.reasonCodes()).containsExactly(MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED, BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "parserVersion", "layoutVersion", "unrelatedName", "unrelatedStandardCode"})
    void checksFullSourceRatherThanOnlyASymbolOrClaimedHash(String change) throws Exception {
        var input = inputs(KOSDAQ, "00", "N", Map.of(), Map.of());
        ObjectNode node = mapper.valueToTree(input.basicInfo());
        ObjectNode master = (ObjectNode) node.get("observation").get("typeResolution").get("matchingResult").get("masterBatch");
        ObjectNode selected = (ObjectNode) master.withArray("marketResults").get(1);
        if (change.equals("hash")) {
            selected.put("inputSha256", "0".repeat(64));
            ((ObjectNode) master.get("collection").withArray("files").get(1)).put("extractedSha256", "0".repeat(64));
        } else if (change.equals("parserVersion") || change.equals("layoutVersion")) {
            for (var market : master.withArray("marketResults")) {
                ((ObjectNode) market).put(change, "UNKNOWN");
            }
        } else {
            ((ObjectNode) selected.withArray("records").get(1)).put(change.equals("unrelatedName") ? "name" : "standardCode",
                    change.equals("unrelatedName") ? "CHANGED NAME" : "KR7999999999");
        }
        var changed = mapper.treeToValue(node, KisStockBasicInfoRestrictionScreeningResult.class);
        var result = policy.evaluate(changed, input.warnings());
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reasonCodes()).containsExactly(MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED);
    }

    @Test
    void refusesTheSameSymbolsWhenTheirRawRowOrderIsDifferent() {
        var input = inputs(KOSDAQ, "00", "N", Map.of(), Map.of());
        var rows = input.warnings().source().source().records().reversed().stream().map(record -> record.rawLine().getBytes(CP949)).toArray(byte[][]::new);
        var reversed = new KisStockMasterParser().parse(KOSDAQ, content(rows));
        var warning = new KisStockMarketWarningObservationPolicy().evaluate(new KisStockMasterMarketWarningParser().parse(reversed));
        assertThat(policy.evaluate(input.basicInfo(), warning).reasonCodes()).containsExactly(MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED);
    }

    @Test
    void rejectsNullAndLegacyV1WithoutReinterpretingPreservedResults() {
        var input = inputs(KOSPI, "00", "N", Map.of(), Map.of());
        var legacy = new KisStockBasicInfoRestrictionScreeningPolicy()
                .evaluate(input.basicInfo().observation(), "KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V1");
        assertThatThrownBy(() -> policy.evaluate(legacy, input.warnings())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.evaluate(null, input.warnings())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> policy.evaluate(input.basicInfo(), null)).isInstanceOf(NullPointerException.class);
        var result = policy.evaluate(input.basicInfo(), input.warnings());
        var other = inputs(KOSDAQ, "03", "Y", Map.of(), Map.of());
        policy.evaluate(other.basicInfo(), other.warnings());
        assertThat(policy.evaluate(input.basicInfo(), input.warnings())).isEqualTo(result);
    }
}
