package com.stock.market.stock.basicinfo.observation.analysis.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.batch;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.fields;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.END;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisStockBasicInfoAnalysisResultTest {
    @Test
    void retainsTheOriginalResponseAndAllPolicyResultsWithoutReplacingEvidence() {
        var original = response();
        var master = batch();
        var screened = screening(master, original);

        var result = new KisStockBasicInfoAnalysisResult(17L, original, screened);

        assertThat(result.observationId()).isEqualTo(17L);
        assertThat(result.response()).isSameAs(original);
        assertThat(result.screeningResult()).isSameAs(screened);
        assertThat(result.response().requestStartedAt()).isEqualTo(START);
        assertThat(result.response().responseReceivedAt()).isEqualTo(END);
        var matching = result.screeningResult().observation().typeResolution().matchingResult();
        assertThat(matching.masterBatch()).isSameAs(master);
        assertThat(matching.apiInput().inputSha256()).isEqualTo(sha256(original.content()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsInvalidObservationId(Long id) {
        var original = response();
        var screened = screening(batch(), original);

        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisResult(id, original, screened))
                .isExactlyInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class)
                .hasMessage(id == null ? "observationId must not be null." : "observationId must be positive.");
    }

    @Test
    void rejectsMissingResponseOrScreeningResult() {
        var original = response();
        var screened = screening(batch(), original);

        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisResult(17L, null, screened))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("response must not be null.");
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisResult(17L, original, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("screeningResult must not be null.");
    }

    @Test
    void rejectsAnalysisForAnotherRequestedSymbolEvenWhenTheBytesAreIdentical() {
        var original = response();
        var otherRequest = new KisStockBasicInfoRawResponse("005930", START, END, 200, original.content());
        var screened = screening(batch(), original);

        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisResult(17L, otherRequest, screened))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Analysis requested symbol must match the stored response.").hasNoCause();
    }

    @Test
    void rejectsAnalysisOfDifferentBytesEvenWhenParsedFieldsAreIdentical() {
        var original = response();
        var padded = new KisStockBasicInfoRawResponse(SYMBOL, START, END, 200,
                (" \n" + new String(original.content(), StandardCharsets.UTF_8) + "\r\n").getBytes(StandardCharsets.UTF_8));
        var screened = screening(batch(), original);

        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisResult(17L, padded, screened))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Analysis input SHA-256 must match the stored response content.").hasNoCause();
    }

    @Test
    void exposesDefensiveBytesAndImmutableReasonLists() {
        var original = response(SYMBOL, fields().put("tr_stop_yn", "Y"));
        var result = new KisStockBasicInfoAnalysisResult(17L, original, screening(batch(), original));
        byte[] bytes = original.content();

        Arrays.fill(result.response().content(), (byte) 0);

        assertThat(result.response().content()).isEqualTo(bytes);
        assertThatThrownBy(() -> result.screeningResult().reasonCodes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "Y", " "})
    void fullJsonRoundTripPreservesRawBytesMetadataAndAllPolicyResults(String suspension) throws Exception {
        var original = response(SYMBOL, fields().put("tr_stop_yn", suspension));
        var result = new KisStockBasicInfoAnalysisResult(17L, original, screening(batch(), original));
        var mapper = new ObjectMapper().findAndRegisterModules();

        var restored = mapper.readValue(mapper.writeValueAsBytes(result), KisStockBasicInfoAnalysisResult.class);

        assertThat(restored).isEqualTo(result);
        assertThat(restored.response().content()).isEqualTo(original.content());
    }

    @Test
    void fullJsonRoundTripRejectsReplacedRawBytesInsteadOfAcceptingOldAnalysis() throws Exception {
        var original = response();
        var result = new KisStockBasicInfoAnalysisResult(17L, original, screening(batch(), original));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var json = mapper.valueToTree(result);
        ((ObjectNode) json.get("response")).put("content",
                Base64.getEncoder().encodeToString("not-the-analyzed-content".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> mapper.treeToValue(json, KisStockBasicInfoAnalysisResult.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Analysis input SHA-256 must match the stored response content.");
    }

    @Test
    void toStringContainsOnlySummaryMetadataNotRawContentOrApiMessage() {
        var original = response();
        var result = new KisStockBasicInfoAnalysisResult(17L, original, screening(batch(), original));

        assertThat(result.toString()).contains("observationId=17", "requestedSymbol=" + SYMBOL,
                        "inputSha256=" + sha256(original.content()), "status=NO_EXCLUSION_SIGNAL_OBSERVED")
                .doesNotContain("00000A" + SYMBOL, KisStockBasicInfoParsingFixture.NAME, KisStockBasicInfoParsingFixture.MESSAGE,
                        new String(original.content(), StandardCharsets.UTF_8), "masterBatch=");
    }
}
