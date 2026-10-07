package com.stock.market.stock.basicinfo.observation.analysis.restriction.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture.analysis;
import static com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture.inputs;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockRestrictionAnalysisResultTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final KisStockRestrictionScreeningPolicy policy = new KisStockRestrictionScreeningPolicy();

    @Test
    void retainsBothCompleteInputsAndTheirIndependentSourceMetadata() {
        var input = inputs(KOSDAQ, "02", "?", Map.of(), Map.of("tr_stop_yn", "Y"));
        var basic = analysis(17L, input);
        var combined = policy.evaluate(basic.screeningResult(), input.warnings());

        var result = new KisStockRestrictionAnalysisResult(basic, combined);

        assertThat(result.basicInfoAnalysis()).isSameAs(basic);
        assertThat(result.restrictionScreeningResult()).isSameAs(combined);
        assertThat(result.basicInfoAnalysis().response()).isSameAs(input.response());
        assertThat(result.restrictionScreeningResult().marketWarningObservation()).isSameAs(input.warnings());
        var matching = result.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult();
        assertThat(matching.masterBatch()).isSameAs(input.master());
        assertThat(matching.masterBatch().collection().startedAt()).isBefore(input.response().requestStartedAt());
        assertThat(result.basicInfoAnalysis().response().requestStartedAt()).isEqualTo(input.response().requestStartedAt());
        assertThat(result.basicInfoAnalysis().response().responseReceivedAt()).isEqualTo(input.response().responseReceivedAt());
        assertThat(matching.apiInput().inputSha256()).isEqualTo(sha256(input.response().content()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"basicInfoAnalysis", "restrictionScreeningResult"})
    void rejectsMissingInputs(String missing) {
        var input = inputs();
        var basic = analysis(17L, input);
        var combined = policy.evaluate(basic.screeningResult(), input.warnings());

        assertThatThrownBy(() -> new KisStockRestrictionAnalysisResult(
                missing.equals("basicInfoAnalysis") ? null : basic,
                missing.equals("restrictionScreeningResult") ? null : combined))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"symbol", "content", "master", "version"})
    void rejectsDifferentCompleteBasicScreeningEvenIfStatusOrSymbolMatches(String difference) {
        var input = inputs();
        var original = analysis(17L, input);
        var combined = policy.evaluate(original.screeningResult(), input.warnings());
        var other = switch (difference) {
            case "symbol" -> analysis(17L, inputs(KOSPI, "00", "N", Map.of(), Map.of()));
            case "content" -> {
                var response = input.response();
                var padded = new KisStockBasicInfoRawResponse(response.requestedSymbol(), response.requestStartedAt(), response.responseReceivedAt(), 200,
                        (" \n" + new String(response.content(), StandardCharsets.UTF_8) + "\r\n").getBytes(StandardCharsets.UTF_8));
                yield new KisStockBasicInfoAnalysisResult(17L, padded, screening(input.master(), padded));
            }
            case "master" -> analysis(17L, inputs(KOSDAQ, "03", "Y", Map.of(), Map.of()));
            case "version" -> new KisStockBasicInfoAnalysisResult(17L, input.response(),
                    new KisStockBasicInfoRestrictionScreeningPolicy().evaluate(original.screeningResult().observation(),
                            KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION_V1));
            default -> throw new IllegalArgumentException("Unexpected test difference.");
        };

        assertThatThrownBy(() -> new KisStockRestrictionAnalysisResult(other, combined))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Restriction screening must use the complete screening result of the basic info analysis.");
    }

    @Test
    void acceptsEqualEvidenceRestoredAsIndependentObjects() throws Exception {
        var input = inputs();
        var original = analysis(17L, input);
        var restored = mapper.readValue(mapper.writeValueAsBytes(original), KisStockBasicInfoAnalysisResult.class);
        var combined = policy.evaluate(restored.screeningResult(), input.warnings());

        var result = new KisStockRestrictionAnalysisResult(original, combined);

        assertThat(restored).isNotSameAs(original).isEqualTo(original);
        assertThat(result.basicInfoAnalysis().screeningResult()).isNotSameAs(combined.basicInfoScreening()).isEqualTo(combined.basicInfoScreening());
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void fullJsonRoundTripsPreserveBothMarketsAllStatusesAndIndependentSourceEvidence(KisStockMasterMarket market) throws Exception {
        for (String code : new String[]{"00", "02", "99"}) {
            var input = inputs(market, code, "N", Map.of(), Map.of());
            var basic = analysis(17L, input);
            var result = new KisStockRestrictionAnalysisResult(basic, policy.evaluate(basic.screeningResult(), input.warnings()));

            var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockRestrictionAnalysisResult.class);

            assertThat(restored).isEqualTo(result);
            assertThat(mapper.<JsonNode>valueToTree(restored)).isEqualTo(mapper.<JsonNode>valueToTree(result));
            assertThat(restored.basicInfoAnalysis().response().content()).isEqualTo(input.response().content());
        }
    }

    @Test
    void fullJsonRoundTripPreservesDisconnectedWarningEvidenceAsReviewNotAsAnException() throws Exception {
        var input = inputs();
        var basic = analysis(17L, input);
        var otherWarnings = KisStockRestrictionScreeningFixture.warnings(input.master(), KOSPI);
        var result = new KisStockRestrictionAnalysisResult(basic, policy.evaluate(basic.screeningResult(), otherWarnings));

        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockRestrictionAnalysisResult.class);

        assertThat(restored).isEqualTo(result);
        assertThat(restored.restrictionScreeningResult().status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(restored.restrictionScreeningResult().marketWarningObservation()).isEqualTo(otherWarnings);
    }

    @Test
    void jsonRejectsIndividuallyValidButUnrelatedAnalysisAndCombinedScreening() {
        var input = inputs();
        var basic = analysis(17L, input);
        var result = new KisStockRestrictionAnalysisResult(basic, policy.evaluate(basic.screeningResult(), input.warnings()));
        var otherInput = inputs(KOSDAQ, "00", "N", Map.of(), Map.of("tr_stop_yn", "Y"));
        var otherBasic = analysis(17L, otherInput);
        var otherCombined = policy.evaluate(otherBasic.screeningResult(), otherInput.warnings());
        ObjectNode json = mapper.valueToTree(result);
        json.set("restrictionScreeningResult", mapper.valueToTree(otherCombined));

        assertThatThrownBy(() -> mapper.treeToValue(json, KisStockRestrictionAnalysisResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Restriction screening must use the complete screening result of the basic info analysis.");
    }

    @Test
    void inheritedByteCopiesAndReasonListImmutabilityRemainIntact() {
        var input = inputs(KOSDAQ, "02", "Y", Map.of(), Map.of("tr_stop_yn", "Y"));
        var basic = analysis(17L, input);
        var result = new KisStockRestrictionAnalysisResult(basic, policy.evaluate(basic.screeningResult(), input.warnings()));

        Arrays.fill(result.basicInfoAnalysis().response().content(), (byte) 0);

        assertThat(result.basicInfoAnalysis().response().content()).isEqualTo(input.response().content());
        assertThatThrownBy(() -> result.basicInfoAnalysis().screeningResult().reasonCodes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.restrictionScreeningResult().reasonCodes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.restrictionScreeningResult().marketWarningObservation().observations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toStringContainsOnlySafeSummaryWithoutRawResponseOrCompleteMasterAndWarnings() {
        var input = inputs();
        var basic = analysis(17L, input);
        var result = new KisStockRestrictionAnalysisResult(basic, policy.evaluate(basic.screeningResult(), input.warnings()));

        assertThat(result.toString()).contains("observationId=17", "requestedSymbol=" + input.response().requestedSymbol(),
                        "inputSha256=" + sha256(input.response().content()), "status=NO_EXCLUSION_SIGNAL_OBSERVED", "reasonCodes=[]")
                .doesNotContain(KisStockBasicInfoParsingFixture.MESSAGE, KisStockBasicInfoParsingFixture.NAME, "rawLine=", "masterBatch=",
                        "marketWarningObservation=", new String(input.response().content(), StandardCharsets.UTF_8), "00000A" + input.response().requestedSymbol());
    }
}
