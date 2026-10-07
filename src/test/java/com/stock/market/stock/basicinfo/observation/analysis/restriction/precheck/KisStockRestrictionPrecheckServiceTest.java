package com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.KisStockRestrictionAnalysisService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessResult;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.KisStockRestrictionScreeningPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.stock.market.stock.basicinfo.observation.analysis.support.KisStockBasicInfoAnalysisFixture.service;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.combine;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.support.KisStockRestrictionPrecheckFixture.freshness;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockRestrictionPrecheckServiceTest {
    private final KisStockRestrictionAnalysisService analysisService = mock(KisStockRestrictionAnalysisService.class);
    private final KisStockRestrictionFreshnessPolicy freshnessPolicy = spy(new KisStockRestrictionFreshnessPolicy());
    private final KisStockRestrictionPrecheckPolicy precheckPolicy = spy(new KisStockRestrictionPrecheckPolicy());
    private final KisStockRestrictionPrecheckService precheckService = new KisStockRestrictionPrecheckService(
            analysisService, freshnessPolicy, precheckPolicy);

    @Test
    void constructionDoesNotReadOrEvaluateObservations() {
        verifyNoInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"analysisService", "freshnessPolicy", "precheckPolicy"})
    void rejectsNullDependencies(String missing) {
        assertThatThrownBy(() -> new KisStockRestrictionPrecheckService(
                missing.equals("analysisService") ? null : analysisService,
                missing.equals("freshnessPolicy") ? null : freshnessPolicy,
                missing.equals("precheckPolicy") ? null : precheckPolicy))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
        verifyNoInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsInvalidObservationIdBeforeCallingDependencies(Long id) {
        var input = input();

        assertThatThrownBy(() -> precheckService.precheck(id, master(input),
                input.restrictionScreeningResult().marketWarningObservation(), request()))
                .isExactlyInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class)
                .hasMessage(id == null ? "observationId must not be null." : "observationId must be positive.");
        verifyNoInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"masterBatch", "marketWarningObservation", "freshnessRequest"})
    void rejectsMissingInputsBeforeReadingObservations(String missing) {
        var input = input();

        assertThatThrownBy(() -> precheckService.precheck(17L, missing.equals("masterBatch") ? null : master(input),
                missing.equals("marketWarningObservation") ? null : input.restrictionScreeningResult().marketWarningObservation(),
                missing.equals("freshnessRequest") ? null : request()))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
        verifyNoInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    @ParameterizedTest
    @MethodSource("statusCombinations")
    void evaluatesEachStageOnceInOrderAndMatchesDirectPolicies(
            KisStockMasterMarket market, KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) {
        var expectedFreshness = freshness(market, restrictionStatus, freshnessStatus);
        var input = expectedFreshness.analysisResult();
        var request = expectedFreshness.request();
        var master = master(input);
        var warnings = input.restrictionScreeningResult().marketWarningObservation();
        when(analysisService.analyze(17L, master, warnings)).thenReturn(input);

        var result = precheckService.precheck(17L, master, warnings, request);

        assertThat(result).isEqualTo(new KisStockRestrictionPrecheckPolicy().evaluate(expectedFreshness));
        assertThat(result.freshnessResult().analysisResult()).isSameAs(input);
        assertThat(result.freshnessResult().request()).isSameAs(request);
        var order = inOrder(analysisService, freshnessPolicy, precheckPolicy);
        order.verify(analysisService).analyze(17L, master, warnings);
        order.verify(freshnessPolicy).evaluate(request, input);
        order.verify(precheckPolicy).evaluate(result.freshnessResult());
        verifyNoMoreInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    @ParameterizedTest
    @EnumSource(KisStockMasterMarket.class)
    void existingAnalysisPathReadsTheStoredRequestOnlyOnce(KisStockMasterMarket market) {
        var input = freshness(market, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
        var store = mock(KisStockBasicInfoObservationStore.class);
        var basicService = spy(service(store));
        var combinedPolicy = spy(new KisStockRestrictionScreeningPolicy());
        var combinedService = spy(new KisStockRestrictionAnalysisService(basicService, combinedPolicy));
        var actualService = new KisStockRestrictionPrecheckService(combinedService, freshnessPolicy, precheckPolicy);
        var request = request();
        when(store.findById(17L)).thenReturn(Optional.of(input.basicInfoAnalysis().response()));

        var result = actualService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request);

        var actualAnalysis = result.freshnessResult().analysisResult();
        assertThat(actualAnalysis).isEqualTo(input);
        var order = inOrder(combinedService, basicService, store, combinedPolicy, freshnessPolicy, precheckPolicy);
        order.verify(combinedService).analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation());
        order.verify(basicService).analyze(17L, master(input));
        order.verify(store).findById(17L);
        order.verify(combinedPolicy).evaluate(actualAnalysis.basicInfoAnalysis().screeningResult(),
                input.restrictionScreeningResult().marketWarningObservation());
        order.verify(freshnessPolicy).evaluate(request, actualAnalysis);
        order.verify(precheckPolicy).evaluate(result.freshnessResult());
        verifyNoMoreInteractions(combinedService, basicService, store, combinedPolicy, freshnessPolicy, precheckPolicy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"analysis", "freshness", "precheck"})
    void propagatesStageFailureWithoutRetryOrCallingTheNextStage(String stage) {
        var input = input();
        var request = request();
        var fresh = new KisStockRestrictionFreshnessPolicy().evaluate(request, input);
        var failure = new DataAccessResourceFailureException("Synthetic " + stage + " failure.");
        when(analysisService.analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation())).thenReturn(input);
        switch (stage) {
            case "analysis" -> doThrow(failure).when(analysisService).analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation());
            case "freshness" -> doThrow(failure).when(freshnessPolicy).evaluate(request, input);
            case "precheck" -> doThrow(failure).when(precheckPolicy).evaluate(fresh);
            default -> throw new IllegalArgumentException("Unexpected fixture stage.");
        }

        assertThatThrownBy(() -> check(input, request)).isSameAs(failure);

        assertCallsThrough(stage, input, request, fresh);
    }

    @ParameterizedTest
    @ValueSource(strings = {"analysis", "freshness", "precheck"})
    void rejectsNullStageResultsWithoutRetryOrCallingTheNextStage(String stage) {
        var input = input();
        var request = request();
        var fresh = new KisStockRestrictionFreshnessPolicy().evaluate(request, input);
        when(analysisService.analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation())).thenReturn(input);
        String message = switch (stage) {
            case "analysis" -> {
                doReturn(null).when(analysisService).analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation());
                yield "Restriction analysis must not be null.";
            }
            case "freshness" -> {
                doReturn(null).when(freshnessPolicy).evaluate(request, input);
                yield "Restriction freshness must not be null.";
            }
            case "precheck" -> {
                doReturn(null).when(precheckPolicy).evaluate(fresh);
                yield "Restriction precheck must not be null.";
            }
            default -> throw new IllegalArgumentException("Unexpected fixture stage.");
        };

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(NullPointerException.class).hasMessage(message);

        assertCallsThrough(stage, input, request, fresh);
    }

    @ParameterizedTest
    @ValueSource(strings = {"observationId", "masterBatch", "marketWarningObservation"})
    void rejectsAnalysisOfDifferentInputsBeforeCheckingFreshness(String changed) {
        var input = input();
        var different = switch (changed) {
            case "observationId" -> new KisStockRestrictionAnalysisResult(new KisStockBasicInfoAnalysisResult(
                    18L, input.basicInfoAnalysis().response(), input.basicInfoAnalysis().screeningResult()), input.restrictionScreeningResult());
            case "masterBatch" -> freshness(KOSPI, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
            case "marketWarningObservation" -> combine(input.basicInfoAnalysis().response(), master(input), warnings(master(input), KOSPI));
            default -> throw new IllegalArgumentException("Unexpected fixture input.");
        };
        when(analysisService.analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation())).thenReturn(different);

        assertThatThrownBy(() -> check(input, request())).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Restriction analysis must preserve the requested observation ID, master batch and market warning observation.");
        verify(analysisService).analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation());
        verifyNoInteractions(freshnessPolicy, precheckPolicy);
        verifyNoMoreInteractions(analysisService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"evaluation", "masterAge", "basicInfoAge", "analysisResult"})
    void rejectsFreshnessOfDifferentConditionsOrAnalysisBeforePrechecking(String changed) {
        var input = input();
        var request = request();
        var otherRequest = new KisStockRestrictionFreshnessRequest(
                changed.equals("evaluation") ? request.evaluatedAt().plusNanos(1) : request.evaluatedAt(),
                changed.equals("masterAge") ? request.maxMasterAge().plusNanos(1) : request.maxMasterAge(),
                changed.equals("basicInfoAge") ? request.maxBasicInfoAge().plusNanos(1) : request.maxBasicInfoAge());
        var otherAnalysis = changed.equals("analysisResult")
                ? freshness(KOSDAQ, EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult() : input;
        var different = new KisStockRestrictionFreshnessPolicy().evaluate(otherRequest, otherAnalysis);
        when(analysisService.analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation())).thenReturn(input);
        doReturn(different).when(freshnessPolicy).evaluate(request, input);

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Restriction freshness must preserve the evaluation request and complete analysis result.");
        assertCallsThrough("freshness", input, request, different);
    }

    @Test
    void rejectsPrecheckOfAnotherCompleteFreshnessResult() {
        var input = input();
        var request = request();
        var fresh = new KisStockRestrictionFreshnessPolicy().evaluate(request, input);
        var otherRequest = new KisStockRestrictionFreshnessRequest(request.evaluatedAt().plusNanos(1), request.maxMasterAge(), request.maxBasicInfoAge());
        var other = new KisStockRestrictionPrecheckPolicy().evaluate(new KisStockRestrictionFreshnessPolicy().evaluate(otherRequest, input));
        when(analysisService.analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation())).thenReturn(input);
        doReturn(other).when(precheckPolicy).evaluate(fresh);

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Restriction precheck must preserve the complete freshness result.");
        assertCallsThrough("precheck", input, request, fresh);
    }

    @Test
    void repeatedCallsUseTheSuppliedConditionsWithoutCachingOrMixingPriorState() {
        var input = input();
        var request = request();
        var later = new KisStockRestrictionFreshnessRequest(request.evaluatedAt().plus(Duration.ofDays(2)), request.maxMasterAge(), request.maxBasicInfoAge());
        when(analysisService.analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation())).thenReturn(input);

        var first = check(input, request);
        var second = check(input, later);
        var third = check(input, request);

        assertThat(third).isEqualTo(first);
        assertThat(second).isNotEqualTo(first);
        var order = inOrder(analysisService, freshnessPolicy, precheckPolicy);
        for (var result : new KisStockRestrictionPrecheckResult[]{first, second, third}) {
            order.verify(analysisService).analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation());
            order.verify(freshnessPolicy).evaluate(result.freshnessResult().request(), input);
            order.verify(precheckPolicy).evaluate(result.freshnessResult());
        }
        verifyNoMoreInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    private KisStockRestrictionPrecheckResult check(KisStockRestrictionAnalysisResult input, KisStockRestrictionFreshnessRequest request) {
        return precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request);
    }

    private void assertCallsThrough(String stage, KisStockRestrictionAnalysisResult input, KisStockRestrictionFreshnessRequest request,
                                    KisStockRestrictionFreshnessResult fresh) {
        var order = inOrder(analysisService, freshnessPolicy, precheckPolicy);
        order.verify(analysisService).analyze(17L, master(input), input.restrictionScreeningResult().marketWarningObservation());
        if (!stage.equals("analysis")) order.verify(freshnessPolicy).evaluate(request, input);
        if (stage.equals("precheck")) order.verify(precheckPolicy).evaluate(fresh);
        verifyNoMoreInteractions(analysisService, freshnessPolicy, precheckPolicy);
    }

    private static KisStockRestrictionAnalysisResult input() {
        return freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
    }

    private static StockMasterBatchParseResult master(KisStockRestrictionAnalysisResult input) {
        return input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
    }

    private static Stream<Arguments> statusCombinations() {
        return Arrays.stream(KisStockMasterMarket.values()).flatMap(market -> Arrays.stream(KisStockRestrictionScreeningStatus.values())
                .flatMap(restriction -> Arrays.stream(KisStockRestrictionFreshnessStatus.values())
                        .map(status -> Arguments.of(market, restriction, status))));
    }
}
