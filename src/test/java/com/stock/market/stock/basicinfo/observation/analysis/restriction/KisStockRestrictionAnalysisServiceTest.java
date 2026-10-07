package com.stock.market.stock.basicinfo.observation.analysis.restriction;

import com.stock.market.stock.basicinfo.observation.analysis.KisStockBasicInfoAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.basicinfo.provider.kis.dto.KisStockBasicInfoRawResponse;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.KisStockBasicInfoRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture.analysis;
import static com.stock.market.stock.basicinfo.observation.analysis.restriction.support.KisStockRestrictionAnalysisFixture.inputs;
import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.service;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_MANAGEMENT_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.basicinfo.screening.result.KisStockBasicInfoRestrictionScreeningReasonCode.BASIC_INFO_SUSPENSION_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.BASIC_INFO_REVIEW_REQUIRED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_INVESTMENT_CAUTION_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_INVESTMENT_RISK_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_INVESTMENT_WARNING_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MARKET_NOT_MATCHED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_REQUESTED_RECORD_NOT_MATCHED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.MARKET_WARNING_VALUE_UNVERIFIED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningReasonCode.STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockRestrictionAnalysisServiceTest {
    private final KisStockBasicInfoAnalysisService basicService = mock(KisStockBasicInfoAnalysisService.class);
    private final KisStockRestrictionScreeningPolicy policy = spy(new KisStockRestrictionScreeningPolicy());
    private final KisStockRestrictionAnalysisService combinedService = new KisStockRestrictionAnalysisService(basicService, policy);

    @Test
    void constructionDoesNotReadOrAnalyzeObservations() {
        verifyNoInteractions(basicService, policy);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void rejectsNullDependencies(int index) {
        assertThatThrownBy(() -> new KisStockRestrictionAnalysisService(index == 0 ? null : basicService, index == 1 ? null : policy))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage((index == 0 ? "basicInfoAnalysisService" : "restrictionScreeningPolicy") + " must not be null.");
        verifyNoInteractions(basicService, policy);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsInvalidObservationIdBeforeCallingDependencies(Long id) {
        var input = inputs();
        assertThatThrownBy(() -> combinedService.analyze(id, input.master(), input.warnings()))
                .isExactlyInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class)
                .hasMessage(id == null ? "observationId must not be null." : "observationId must be positive.");
        verifyNoInteractions(basicService, policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"masterBatch", "marketWarningObservation"})
    void rejectsNullInputsBeforeCallingDependencies(String missing) {
        var input = inputs();
        assertThatThrownBy(() -> combinedService.analyze(17L,
                missing.equals("masterBatch") ? null : input.master(), missing.equals("marketWarningObservation") ? null : input.warnings()))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
        verifyNoInteractions(basicService, policy);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void readsOnceAndCombinesExistingAnalysisWithoutChangingStoredRequestsOrEvidence(KisStockMasterMarket market) {
        var input = inputs(market, "00", "N", Map.of(), Map.of("pdno", "UNRELATED-API-PRODUCT"));
        var store = mock(KisStockBasicInfoObservationStore.class);
        var actualBasicService = spy(service(store));
        var actualService = new KisStockRestrictionAnalysisService(actualBasicService, policy);
        when(store.findById(17L)).thenReturn(Optional.of(input.response()));

        var result = actualService.analyze(17L, input.master(), input.warnings());

        var basic = result.basicInfoAnalysis();
        assertThat(basic.observationId()).isEqualTo(17L);
        assertThat(basic.response()).isSameAs(input.response());
        assertThat(result.restrictionScreeningResult().basicInfoScreening()).isSameAs(basic.screeningResult());
        assertThat(result.restrictionScreeningResult().marketWarningObservation()).isSameAs(input.warnings());
        assertThat(result.restrictionScreeningResult().status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.restrictionScreeningResult().reasonCodes()).isEmpty();
        var matching = basic.screeningResult().observation().typeResolution().matchingResult();
        assertThat(matching.requestedSymbol()).isEqualTo(input.response().requestedSymbol());
        assertThat(matching.masterBatch()).isSameAs(input.master());
        var order = inOrder(actualBasicService, store, policy);
        order.verify(actualBasicService).analyze(17L, input.master());
        order.verify(store).findById(17L);
        order.verify(policy).evaluate(basic.screeningResult(), input.warnings());
        verifyNoMoreInteractions(actualBasicService, store, policy);
    }

    @ParameterizedTest
    @MethodSource("warningCases")
    void preservesWarningAndPreannouncementReasonsWithoutReplacingTheBasicAnalysis(
            String code, String preannouncement, KisStockRestrictionScreeningStatus status,
            List<KisStockRestrictionScreeningReasonCode> reasons
    ) {
        var input = inputs(KOSDAQ, code, preannouncement, Map.of(), Map.of());
        var basic = analysis(17L, input);
        when(basicService.analyze(17L, input.master())).thenReturn(basic);

        var result = combinedService.analyze(17L, input.master(), input.warnings());

        assertThat(result.basicInfoAnalysis()).isSameAs(basic);
        assertThat(result.restrictionScreeningResult().status()).isEqualTo(status);
        assertThat(result.restrictionScreeningResult().reasonCodes()).isEqualTo(reasons);
        verify(basicService).analyze(17L, input.master());
        verify(policy).evaluate(basic.screeningResult(), input.warnings());
        verifyNoMoreInteractions(basicService, policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "Y"})
    void retainsBasicUnverifiedAndExclusionReasonsAlongsideWarningReasons(String suspension) {
        var input = inputs(KOSDAQ, "02", "?", Map.of(), Map.of("tr_stop_yn", suspension, "admn_item_yn", " "));
        var basic = analysis(17L, input);
        when(basicService.analyze(17L, input.master())).thenReturn(basic);

        var result = combinedService.analyze(17L, input.master(), input.warnings());

        assertThat(result.basicInfoAnalysis().screeningResult().reasonCodes()).contains(BASIC_INFO_MANAGEMENT_VALUE_UNVERIFIED);
        if (suspension.equals("Y")) {
            assertThat(result.basicInfoAnalysis().screeningResult().reasonCodes()).contains(BASIC_INFO_SUSPENSION_Y_OBSERVED);
        }
        assertThat(result.restrictionScreeningResult().status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(result.restrictionScreeningResult().reasonCodes()).containsExactly(
                suspension.equals("Y") ? BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED : BASIC_INFO_REVIEW_REQUIRED,
                MARKET_WARNING_INVESTMENT_WARNING_OBSERVED, MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED);
    }

    @Test
    void identityMismatchRemainsReviewRequiredEvenWithBoundWarningSignals() {
        var input = inputs(KOSDAQ, "03", "Y", Map.of(), Map.of("std_pdno", "KR7000250001"));
        when(basicService.analyze(17L, input.master())).thenReturn(analysis(17L, input));

        var result = combinedService.analyze(17L, input.master(), input.warnings());

        assertThat(result.restrictionScreeningResult().status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.restrictionScreeningResult().reasonCodes()).containsExactly(
                STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED, BASIC_INFO_REVIEW_REQUIRED,
                MARKET_WARNING_INVESTMENT_RISK_OBSERVED, MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"market", "master"})
    void disconnectedWarningsRemainReviewRequiredAndAreNotAttachedToTheRequestedStock(String difference) {
        var input = inputs(KOSDAQ, "00", "N", Map.of(), Map.of("tr_stop_yn", "Y"));
        var warning = difference.equals("market") ? KisStockRestrictionScreeningFixture.warnings(input.master(), KOSPI)
                : inputs(KOSDAQ, "03", "Y", Map.of(), Map.of()).warnings();
        when(basicService.analyze(17L, input.master())).thenReturn(analysis(17L, input));

        var result = combinedService.analyze(17L, input.master(), warning);

        assertThat(result.restrictionScreeningResult().marketWarningObservation()).isSameAs(warning);
        assertThat(result.restrictionScreeningResult().status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.restrictionScreeningResult().reasonCodes()).containsExactly(
                difference.equals("market") ? MARKET_WARNING_MARKET_NOT_MATCHED : MARKET_WARNING_MASTER_SOURCE_NOT_MATCHED,
                BASIC_INFO_EXCLUSION_SIGNAL_OBSERVED);
    }

    @Test
    void missingRequestedStockRemainsReviewRequiredWithoutBorrowingAnotherWarningRow() {
        var input = inputs(KOSDAQ, "03", "Y", Map.of(), Map.of());
        var original = input.response();
        var missing = new KisStockBasicInfoRawResponse("999999", original.requestStartedAt(), original.responseReceivedAt(),
                original.httpStatus(), original.content());
        var store = mock(KisStockBasicInfoObservationStore.class);
        when(store.findById(17L)).thenReturn(Optional.of(missing));

        var result = new KisStockRestrictionAnalysisService(service(store), policy).analyze(17L, input.master(), input.warnings());

        assertThat(result.basicInfoAnalysis().response().requestedSymbol()).isEqualTo("999999");
        assertThat(result.restrictionScreeningResult().status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.restrictionScreeningResult().reasonCodes()).containsExactly(
                STANDARD_CODE_AND_MARKET_MATCH_NOT_CONFIRMED, MARKET_WARNING_REQUESTED_RECORD_NOT_MATCHED, BASIC_INFO_REVIEW_REQUIRED);
        verify(store).findById(17L);
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest
    @MethodSource("analysisFailures")
    void propagatesExistingAnalysisFailuresWithoutRetryOrScreening(RuntimeException failure) {
        var input = inputs();
        when(basicService.analyze(17L, input.master())).thenThrow(failure);

        assertThatThrownBy(() -> combinedService.analyze(17L, input.master(), input.warnings())).isSameAs(failure);

        verify(basicService).analyze(17L, input.master());
        verifyNoMoreInteractions(basicService);
        verifyNoInteractions(policy);
    }

    @Test
    void propagatesScreeningFailureWithoutReanalyzingTheObservation() {
        var input = inputs();
        var basic = analysis(17L, input);
        var failure = new IllegalArgumentException("Synthetic screening failure.");
        when(basicService.analyze(17L, input.master())).thenReturn(basic);
        doThrow(failure).when(policy).evaluate(basic.screeningResult(), input.warnings());

        assertThatThrownBy(() -> combinedService.analyze(17L, input.master(), input.warnings())).isSameAs(failure);

        verify(basicService).analyze(17L, input.master());
        verify(policy).evaluate(basic.screeningResult(), input.warnings());
        verifyNoMoreInteractions(basicService, policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "master"})
    void rejectsAnUpstreamAnalysisForAnotherRequestedIdOrMaster(String difference) {
        var input = inputs();
        var other = difference.equals("id") ? analysis(18L, input)
                : analysis(17L, inputs(KOSDAQ, "03", "Y", Map.of(), Map.of()));
        when(basicService.analyze(17L, input.master())).thenReturn(other);

        assertThatThrownBy(() -> combinedService.analyze(17L, input.master(), input.warnings()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Basic info analysis must preserve the requested observation ID and master batch.");

        verify(basicService).analyze(17L, input.master());
        verifyNoMoreInteractions(basicService);
        verifyNoInteractions(policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "warning"})
    void rejectsPolicyOutputBuiltFromDifferentInputs(String difference) {
        var input = inputs();
        var basic = analysis(17L, input);
        var otherBasic = analysis(17L, inputs(KOSDAQ, "00", "N", Map.of(), Map.of("tr_stop_yn", "Y")));
        var otherWarning = inputs(KOSDAQ, "03", "Y", Map.of(), Map.of()).warnings();
        var replaced = new KisStockRestrictionScreeningPolicy().evaluate(
                difference.equals("basic") ? otherBasic.screeningResult() : basic.screeningResult(),
                difference.equals("warning") ? otherWarning : input.warnings());
        when(basicService.analyze(17L, input.master())).thenReturn(basic);
        doReturn(replaced).when(policy).evaluate(basic.screeningResult(), input.warnings());

        assertThatThrownBy(() -> combinedService.analyze(17L, input.master(), input.warnings()))
                .isExactlyInstanceOf(difference.equals("basic") ? IllegalArgumentException.class : IllegalStateException.class)
                .hasMessage(difference.equals("basic")
                        ? "Restriction screening must use the complete screening result of the basic info analysis."
                        : "Restriction screening must preserve the supplied market warning observation.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"basic", "screening"})
    void rejectsNullDependencyResults(String stage) {
        var input = inputs();
        if (stage.equals("screening")) {
            var basic = analysis(17L, input);
            when(basicService.analyze(17L, input.master())).thenReturn(basic);
            doReturn(null).when(policy).evaluate(basic.screeningResult(), input.warnings());
        }

        assertThatThrownBy(() -> combinedService.analyze(17L, input.master(), input.warnings()))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage((stage.equals("basic") ? "basicInfoAnalysis" : "restrictionScreeningResult") + " must not be null.");
        if (stage.equals("basic")) {
            verifyNoInteractions(policy);
        }
    }

    @Test
    void rejectsLegacyV1AnalysisWithoutRelabelingItsVersion() {
        var input = inputs();
        var v2 = analysis(17L, input);
        var v1Screening = new KisStockBasicInfoRestrictionScreeningPolicy().evaluate(v2.screeningResult().observation(),
                KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION_V1);
        var v1 = new KisStockBasicInfoAnalysisResult(17L, input.response(), v1Screening);
        when(basicService.analyze(17L, input.master())).thenReturn(v1);

        assertThatThrownBy(() -> combinedService.analyze(17L, input.master(), input.warnings()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Combined restriction screening requires the supported basic info V2 and market warning V1 contracts.");
        assertThat(v1.screeningResult().screeningVersion()).isEqualTo(KisStockBasicInfoRestrictionScreeningPolicy.SCREENING_VERSION_V1);
    }

    @Test
    void sharedWarningsCanBeReusedAcrossRequestsWithoutKeepingPreviousAnalysisState() {
        var input = inputs();
        var store = mock(KisStockBasicInfoObservationStore.class);
        var suspended = inputs(KOSDAQ, "00", "N", Map.of(), Map.of("tr_stop_yn", "Y")).response();
        when(store.findById(17L)).thenReturn(Optional.of(input.response()));
        when(store.findById(18L)).thenReturn(Optional.of(suspended));
        var actual = new KisStockRestrictionAnalysisService(service(store), policy);

        var first = actual.analyze(17L, input.master(), input.warnings());
        var second = actual.analyze(18L, input.master(), input.warnings());
        var repeated = actual.analyze(17L, input.master(), input.warnings());

        assertThat(first).isEqualTo(repeated);
        assertThat(first.restrictionScreeningResult().status()).isEqualTo(NO_EXCLUSION_SIGNAL_OBSERVED);
        assertThat(second.restrictionScreeningResult().status()).isEqualTo(EXCLUSION_SIGNAL_OBSERVED);
        assertThat(first.restrictionScreeningResult().marketWarningObservation()).isSameAs(second.restrictionScreeningResult().marketWarningObservation());
        verify(store, times(2)).findById(17L);
        verify(store).findById(18L);
        verifyNoMoreInteractions(store);
    }

    private static Stream<Arguments> warningCases() {
        return Stream.of(
                Arguments.of("00", "N", NO_EXCLUSION_SIGNAL_OBSERVED, List.of()),
                Arguments.of("01", "N", EXCLUSION_SIGNAL_OBSERVED, List.of(MARKET_WARNING_INVESTMENT_CAUTION_OBSERVED)),
                Arguments.of("02", "N", EXCLUSION_SIGNAL_OBSERVED, List.of(MARKET_WARNING_INVESTMENT_WARNING_OBSERVED)),
                Arguments.of("03", "N", EXCLUSION_SIGNAL_OBSERVED, List.of(MARKET_WARNING_INVESTMENT_RISK_OBSERVED)),
                Arguments.of("00", "Y", EXCLUSION_SIGNAL_OBSERVED, List.of(MARKET_WARNING_RISK_PREANNOUNCEMENT_Y_OBSERVED)),
                Arguments.of("99", "N", REVIEW_REQUIRED, List.of(MARKET_WARNING_VALUE_UNVERIFIED)),
                Arguments.of("00", "?", REVIEW_REQUIRED, List.of(MARKET_WARNING_RISK_PREANNOUNCEMENT_VALUE_UNVERIFIED)));
    }

    private static Stream<RuntimeException> analysisFailures() {
        return Stream.of(new NoSuchElementException("Synthetic missing observation."),
                new DataAccessResourceFailureException("Synthetic database failure."),
                new IllegalStateException("Synthetic stored content failure."), new IllegalArgumentException("Synthetic parse failure."));
    }
}
