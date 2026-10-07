package com.stock.market.stock.basicinfo.observation.analysis;

import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.basicinfo.provider.kis.parsing.KisStockBasicInfoParser;
import com.stock.market.stock.basicinfo.provider.kis.parsing.result.KisStockBasicInfoParseResult;
import com.stock.market.stock.basicinfo.provider.kis.parsing.support.KisStockBasicInfoParsingFixture;
import com.stock.market.stock.master.matching.kisbasicinfo.KisStockBasicInfoMatchingPolicy;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchReasonCode;
import com.stock.market.stock.master.matching.kisbasicinfo.result.KisStockBasicInfoMatchResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.KisStockBasicInfoTypeResolutionPolicy;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionReasonCode;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.result.KisStockBasicInfoTypeResolutionResult;
import com.stock.strategy.universe.eligibility.classification.kis.basicinfo.resolution.support.KisStockBasicInfoTypeResolutionFixture;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.KisStockBasicInfoRestrictionObservationPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.result.KisStockBasicInfoRestrictionObservationResult;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.batch;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.fields;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.response;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.screening;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.END;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.START;
import static com.stock.market.stock.basicinfo.observation.support.KisStockBasicInfoObservationFixture.SYMBOL;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_MANAGEMENT_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_SUSPENSION_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockBasicInfoAnalysisServiceTest {
    private final KisStockBasicInfoObservationStore store = mock(KisStockBasicInfoObservationStore.class);
    private final KisStockBasicInfoParser parser = spy(new KisStockBasicInfoParser());
    private final KisStockBasicInfoMatchingPolicy matchingPolicy = spy(new KisStockBasicInfoMatchingPolicy());
    private final KisStockBasicInfoTypeResolutionPolicy typePolicy = spy(KisStockBasicInfoTypeResolutionFixture.policy());
    private final KisStockBasicInfoRestrictionObservationPolicy observationPolicy = spy(new KisStockBasicInfoRestrictionObservationPolicy());
    private final KisStockBasicInfoRestrictionScreeningPolicy screeningPolicy = spy(new KisStockBasicInfoRestrictionScreeningPolicy());
    private final KisStockBasicInfoAnalysisService service = new KisStockBasicInfoAnalysisService(
            store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);

    @Test
    void constructionDoesNotReadOrAnalyzeObservations() {
        verifyNoInteractions(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rejectsNullDependencies(int index) {
        String[] names = {"store", "parser", "matchingPolicy", "typeResolutionPolicy",
                "restrictionObservationPolicy", "restrictionScreeningPolicy"};
        assertThatThrownBy(() -> new KisStockBasicInfoAnalysisService(index == 0 ? null : store,
                index == 1 ? null : parser, index == 2 ? null : matchingPolicy, index == 3 ? null : typePolicy,
                index == 4 ? null : observationPolicy, index == 5 ? null : screeningPolicy))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(names[index] + " must not be null.");
        verifyNoInteractions(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsInvalidObservationIdBeforeReadingOrParsing(Long id) {
        var master = batch();
        assertThatThrownBy(() -> service.analyze(id, master))
                .isExactlyInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class)
                .hasMessage(id == null ? "observationId must not be null." : "observationId must be positive.");
        verifyNoInteractions(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @Test
    void rejectsMissingMasterBeforeReadingAnObservation() {
        assertThatThrownBy(() -> service.analyze(17L, null))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage("masterBatch must not be null.");
        verifyNoInteractions(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @Test
    void missingObservationStopsWithoutParsingSavingOrFallbackLookup() {
        when(store.findById(17L)).thenReturn(Optional.empty());
        var master = batch();

        assertThatThrownBy(() -> service.analyze(17L, master)).isExactlyInstanceOf(NoSuchElementException.class)
                .hasMessage("KIS stock basic info observation not found. id=17").hasNoCause();

        verify(store).findById(17L);
        verifyNoMoreInteractions(store);
        verifyNoInteractions(parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void propagatesStorageFailureWithoutRetryOrDownstreamAnalysis(RuntimeException failure) {
        when(store.findById(17L)).thenThrow(failure);
        var master = batch();

        assertThatThrownBy(() -> service.analyze(17L, master)).isSameAs(failure);

        verify(store).findById(17L);
        verifyNoMoreInteractions(store);
        verifyNoInteractions(parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @Test
    void readsOnceAndRunsExistingPoliciesInOrderUsingStoredRequestMetadata() {
        var original = response();
        var master = batch();
        var expected = screening(master, original);
        when(store.findById(17L)).thenReturn(Optional.of(original));

        var result = service.analyze(17L, master);

        assertThat(result.observationId()).isEqualTo(17L);
        assertThat(result.response()).isSameAs(original);
        assertThat(result.screeningResult()).isEqualTo(expected);
        var matching = result.screeningResult().observation().typeResolution().matchingResult();
        assertThat(matching.masterBatch()).isSameAs(master);
        assertThat(matching.requestedSymbol()).isEqualTo(SYMBOL);
        assertThat(matching.apiInput().rawRecord().productNumber()).isEqualTo("00000A" + SYMBOL);
        var order = inOrder(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
        order.verify(store).findById(17L);
        order.verify(parser).parse(original.content());
        order.verify(matchingPolicy).match(same(master), eq(SYMBOL), same(matching.apiInput()));
        order.verify(typePolicy).resolve(same(matching));
        order.verify(observationPolicy).evaluate(same(result.screeningResult().observation().typeResolution()));
        order.verify(screeningPolicy).evaluate(same(result.screeningResult().observation()));
        verifyNoMoreInteractions(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @Test
    void repeatedAnalysisKeepsTheSameEvidenceWithoutCreatingOrUpdatingObservations() {
        var original = response();
        var master = batch();
        when(store.findById(17L)).thenReturn(Optional.of(original));

        assertThat(service.analyze(17L, master)).isEqualTo(service.analyze(17L, master));

        verify(store, times(2)).findById(17L);
        verifyNoMoreInteractions(store);
        assertThat(master.collection().finishedAt()).isNotEqualTo(original.responseReceivedAt());
    }

    @ParameterizedTest
    @MethodSource("unparseableContents")
    void businessFailureOrMalformedPayloadStopsBeforeMatchingWithoutChangingEvidence(byte[] bytes) {
        var original = new KisStockBasicInfoRawResponse(SYMBOL, START, END, 200, bytes);
        var master = batch();
        when(store.findById(17L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.analyze(17L, master)).isExactlyInstanceOf(IllegalArgumentException.class)
                .hasNoCause();

        assertThat(original.content()).isEqualTo(bytes);
        verify(store).findById(17L);
        verify(parser).parse(bytes);
        verifyNoMoreInteractions(store, parser);
        verifyNoInteractions(matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void downstreamFailureIsPropagatedWithoutRetryOrRunningLaterStages(int stage) {
        var original = response();
        var master = batch();
        var failure = new IllegalStateException("Synthetic analysis failure.");
        when(store.findById(17L)).thenReturn(Optional.of(original));
        switch (stage) {
            case 0 -> doThrow(failure).when(parser).parse(any(byte[].class));
            case 1 -> doThrow(failure).when(matchingPolicy).match(same(master), eq(SYMBOL), any(KisStockBasicInfoParseResult.class));
            case 2 -> doThrow(failure).when(typePolicy).resolve(any(KisStockBasicInfoMatchResult.class));
            case 3 -> doThrow(failure).when(observationPolicy).evaluate(any(KisStockBasicInfoTypeResolutionResult.class));
            case 4 -> doThrow(failure).when(screeningPolicy).evaluate(any(KisStockBasicInfoRestrictionObservationResult.class));
            default -> throw new IllegalArgumentException("Unexpected test stage.");
        }

        assertThatThrownBy(() -> service.analyze(17L, master)).isSameAs(failure);

        verify(store).findById(17L);
        verify(parser).parse(original.content());
        if (stage >= 1) {
            verify(matchingPolicy).match(same(master), eq(SYMBOL), any(KisStockBasicInfoParseResult.class));
        }
        if (stage >= 2) {
            verify(typePolicy).resolve(any(KisStockBasicInfoMatchResult.class));
        }
        if (stage >= 3) {
            verify(observationPolicy).evaluate(any(KisStockBasicInfoTypeResolutionResult.class));
        }
        if (stage >= 4) {
            verify(screeningPolicy).evaluate(any(KisStockBasicInfoRestrictionObservationResult.class));
        }
        verifyNoMoreInteractions(store, parser, matchingPolicy, typePolicy, observationPolicy, screeningPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STANDARD_CODE_MISMATCH", "MARKET_MISMATCH", "API_MARKET_UNVERIFIED", "REQUESTED_SYMBOL_NOT_FOUND"})
    void matchingFailuresRemainReviewRequiredWithoutApiTypeOrRestrictionObservations(String reason) {
        var output = fields();
        String symbol = SYMBOL;
        switch (KisStockBasicInfoMatchReasonCode.valueOf(reason)) {
            case STANDARD_CODE_MISMATCH -> output.put("std_pdno", "KR7005930003");
            case MARKET_MISMATCH -> output.put("mket_id_cd", "STK");
            case API_MARKET_UNVERIFIED -> output.put("mket_id_cd", "UNKNOWN");
            case REQUESTED_SYMBOL_NOT_FOUND -> symbol = "999999";
            default -> throw new IllegalArgumentException("Unexpected test reason.");
        }
        var original = response(symbol, output.put("tr_stop_yn", "Y"));
        when(store.findById(17L)).thenReturn(Optional.of(original));

        var result = service.analyze(17L, batch()).screeningResult();

        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.reasonCodes()).containsExactly(STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED);
        var type = result.observation().typeResolution();
        assertThat(type.matchingResult().reasonCode()).isEqualTo(KisStockBasicInfoMatchReasonCode.valueOf(reason));
        assertThat(type.basicInfoClassification()).isNull();
        assertThat(type.referenceSecurityType()).isNull();
        assertThat(result.observation().basicInfoSuspensionStatus()).isNull();
        assertThat(result.observation().basicInfoManagementStatus()).isNull();
    }

    @Test
    void retainsBothExclusionSignalAndUnverifiedValueInsteadOfDiscardingReasons() {
        when(store.findById(17L)).thenReturn(Optional.of(response(SYMBOL,
                fields().put("tr_stop_yn", "Y").put("admn_item_yn", " "))));

        var result = service.analyze(17L, batch()).screeningResult();

        assertThat(result.status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).containsExactly(BASIC_INFO_SUSPENSION_Y_OBSERVED, BASIC_INFO_MANAGEMENT_VALUE_UNVERIFIED);
    }

    @Test
    void kospiCommonStockKeepsMissingObservationAndSeparateV2ApplicabilityExplanation() {
        var output = fields().put("std_pdno", "KR7005930003").put("mket_id_cd", "STK");
        when(store.findById(17L)).thenReturn(Optional.of(response("005930", output)));

        var result = service.analyze(17L, batch()).screeningResult();

        assertThat(result.status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.reasonCodes()).containsExactly(MASTER_INVESTMENT_CAUTION_FIELD_NOT_PROVIDED,
                MASTER_INVESTMENT_CAUTION_NOT_APPLICABLE);
        assertThat(result.screeningVersion()).isEqualTo("KIS_STOCK_BASIC_INFO_RESTRICTION_SCREENING_V2");
        assertThat(result.observation().typeResolution().masterClassification().rawRecord().rawInvestmentCaution()).isNull();
    }

    @Test
    void typeConflictKeepsNullReferenceTypeAndDoesNotAlterRestrictionScreeningRules() {
        when(store.findById(17L)).thenReturn(Optional.of(response("111111",
                fields().put("std_pdno", "KR7111111111").put("mket_id_cd", "STK"))));

        var result = service.analyze(17L, batch()).screeningResult();

        assertThat(result.observation().typeResolution().reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.TYPE_CONFLICT);
        assertThat(result.observation().typeResolution().referenceSecurityType()).isNull();
        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
    }

    @Test
    void noRestrictionSignalDoesNotFillAnUnverifiedTypeListingDateOrNxtPermission() {
        var original = response(SYMBOL, fields().put("stck_kind_cd", "UNKNOWN")
                .put("nxt_tr_stop_yn", "Y").put("cptt_trad_tr_psbl_yn", "N"));
        when(store.findById(17L)).thenReturn(Optional.of(original));

        var result = service.analyze(17L, batch()).screeningResult();

        assertThat(result.status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.observation().typeResolution().reasonCode()).isEqualTo(KisStockBasicInfoTypeResolutionReasonCode.TYPE_UNVERIFIED);
        assertThat(result.observation().typeResolution().referenceSecurityType()).isNull();
        var raw = result.observation().typeResolution().matchingResult().apiInput().rawRecord();
        assertThat(raw.rawKosdaqListingDate()).isEqualTo("UNKNOWN_KOSDAQ_DATE");
        assertThat(raw.rawNxtSuspension()).isEqualTo("Y");
        assertThat(raw.rawCompetitiveTradingPermission()).isEqualTo("N");
    }

    private static Stream<RuntimeException> storageFailures() {
        return Stream.of(new DataAccessResourceFailureException("Synthetic database failure."),
                new IllegalStateException("Stored KIS stock basic info content integrity check failed. id=17"));
    }

    private static Stream<byte[]> unparseableContents() {
        return Stream.of(KisStockBasicInfoParsingFixture.json("{\"rt_cd\":\"1\",\"msg1\":\"Synthetic failure\"}"),
                KisStockBasicInfoParsingFixture.json("not-json"),
                KisStockBasicInfoParsingFixture.json("{\"rt_cd\":\"0\"}"),
                KisStockBasicInfoParsingFixture.json(new String(response().content(), StandardCharsets.UTF_8) + " {}"));
    }
}
