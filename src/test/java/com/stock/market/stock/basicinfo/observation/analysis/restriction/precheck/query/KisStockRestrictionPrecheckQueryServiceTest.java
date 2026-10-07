package com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.query;

import com.stock.market.stock.basicinfo.observation.analysis.restriction.precheck.KisStockRestrictionPrecheckService;
import com.stock.market.stock.basicinfo.observation.analysis.restriction.result.KisStockRestrictionAnalysisResult;
import com.stock.market.stock.basicinfo.observation.analysis.result.KisStockBasicInfoAnalysisResult;
import com.stock.market.stock.basicinfo.observation.storage.KisStockBasicInfoObservationStore;
import com.stock.market.stock.master.parsing.result.StockMasterBatchParseResult;
import com.stock.market.stock.master.provider.kis.KisStockMasterMarket;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.KisStockRestrictionFreshnessPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.request.KisStockRestrictionFreshnessRequest;
import com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.KisStockRestrictionPrecheckPolicy;
import com.stock.strategy.universe.eligibility.restriction.kis.precheck.result.KisStockRestrictionPrecheckResult;
import com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSDAQ;
import static com.stock.market.stock.master.provider.kis.KisStockMasterMarket.KOSPI;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.result.KisStockRestrictionFreshnessStatus.FRESH;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.combine;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.request;
import static com.stock.strategy.universe.eligibility.restriction.kis.freshness.support.KisStockRestrictionFreshnessFixture.withMarketTimes;
import static com.stock.strategy.universe.eligibility.restriction.kis.precheck.support.KisStockRestrictionPrecheckFixture.freshness;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.result.KisStockRestrictionScreeningStatus.NO_EXCLUSION_SIGNAL_OBSERVED;
import static com.stock.strategy.universe.eligibility.restriction.kis.screening.support.KisStockRestrictionScreeningFixture.warnings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KisStockRestrictionPrecheckQueryServiceTest {
    private final KisStockBasicInfoObservationStore store = mock(KisStockBasicInfoObservationStore.class);
    private final KisStockRestrictionPrecheckService precheckService = mock(KisStockRestrictionPrecheckService.class);
    private final KisStockRestrictionPrecheckQueryService queryService = new KisStockRestrictionPrecheckQueryService(store, precheckService);

    @Test
    void constructionDoesNotSelectOrPrecheckObservations() {
        verifyNoInteractions(store, precheckService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"store", "precheckService"})
    void rejectsNullDependencies(String missing) {
        assertThatThrownBy(() -> new KisStockRestrictionPrecheckQueryService(
                missing.equals("store") ? null : store, missing.equals("precheckService") ? null : precheckService))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
        verifyNoInteractions(store, precheckService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00593", "0059300", "0004y0", " 005930", "005930 ", "005-30"})
    void rejectsInvalidSymbolBeforeSelectingObservations(String symbol) {
        var input = input();

        assertThatThrownBy(() -> queryService.precheckLatest(symbol, master(input),
                input.restrictionScreeningResult().marketWarningObservation(), request()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("symbol must be exactly 6 uppercase alphanumeric characters.");
        verifyNoInteractions(store, precheckService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"masterBatch", "marketWarningObservation", "freshnessRequest"})
    void rejectsMissingInputsBeforeSelectingObservations(String missing) {
        var input = input();

        assertThatThrownBy(() -> queryService.precheckLatest(symbol(input), missing.equals("masterBatch") ? null : master(input),
                missing.equals("marketWarningObservation") ? null : input.restrictionScreeningResult().marketWarningObservation(),
                missing.equals("freshnessRequest") ? null : request()))
                .isExactlyInstanceOf(NullPointerException.class).hasMessage(missing + " must not be null.");
        verifyNoInteractions(store, precheckService);
    }

    @Test
    void returnsEmptyOnlyWhenSelectionFindsNoAvailableObservation() {
        var input = input();
        var request = request();
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.empty());

        assertThat(check(input, request)).isEmpty();

        verify(store).findLatestObservationId(symbol(input), request.evaluatedAt());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(precheckService);
    }

    @ParameterizedTest
    @MethodSource("statusCombinations")
    void selectsAndPrechecksOnceInOrderWithoutChangingStatusOrEvidence(
            KisStockMasterMarket market, KisStockRestrictionScreeningStatus restrictionStatus,
            KisStockRestrictionFreshnessStatus freshnessStatus
    ) {
        var expectedFreshness = freshness(market, restrictionStatus, freshnessStatus);
        var input = expectedFreshness.analysisResult();
        var request = expectedFreshness.request();
        var master = master(input);
        var warning = input.restrictionScreeningResult().marketWarningObservation();
        var expected = new KisStockRestrictionPrecheckPolicy().evaluate(expectedFreshness);
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.of(17L));
        when(precheckService.precheck(17L, master, warning, request)).thenReturn(expected);

        var result = queryService.precheckLatest(symbol(input), master, warning, request).orElseThrow();

        assertThat(result).isSameAs(expected);
        assertThat(result.freshnessResult().request()).isSameAs(request);
        assertThat(result.freshnessResult().analysisResult()).isSameAs(input);
        var order = inOrder(store, precheckService);
        order.verify(store).findLatestObservationId(symbol(input), request.evaluatedAt());
        order.verify(precheckService).precheck(eq(17L), same(master), same(warning), same(request));
        verifyNoMoreInteractions(store, precheckService);
    }

    @Test
    void rejectsNullSelectionInsteadOfTreatingItAsMissingObservation() {
        var input = input();
        var request = request();
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(null);

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Stored observation ID selection must not be null.");
        verify(store).findLatestObservationId(symbol(input), request.evaluatedAt());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(precheckService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void rejectsNonPositiveSelectedIdBeforePrechecking(Long selectedId) {
        var input = input();
        var request = request();
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.of(selectedId));

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Selected observation ID must be positive.");
        verify(store).findLatestObservationId(symbol(input), request.evaluatedAt());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(precheckService);
    }

    @Test
    void propagatesSelectionFailureWithoutReturningEmptyOrCallingPrecheck() {
        var input = input();
        var request = request();
        var failure = new DataAccessResourceFailureException("Synthetic selection failure.");
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenThrow(failure);

        assertThatThrownBy(() -> check(input, request)).isSameAs(failure);
        verify(store).findLatestObservationId(symbol(input), request.evaluatedAt());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(precheckService);
    }

    @ParameterizedTest
    @MethodSource("precheckFailures")
    void propagatesPrecheckFailureWithoutRetryFallbackOrReturningEmpty(RuntimeException failure) {
        var input = input();
        var request = request();
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.of(17L));
        when(precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request))
                .thenThrow(failure);

        assertThatThrownBy(() -> check(input, request)).isSameAs(failure);
        assertSingleCall(input, request);
    }

    @Test
    void rejectsNullPrecheckInsteadOfReturningEmpty() {
        var input = input();
        var request = request();
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.of(17L));
        when(precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request))
                .thenReturn(null);

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Restriction precheck must not be null.");
        assertSingleCall(input, request);
    }

    @Test
    void rejectsOtherSymbolEvenWhenSelectedIdAndAllEvaluationInputsMatch() {
        var input = input();
        var request = request();
        when(store.findLatestObservationId("005930", request.evaluatedAt())).thenReturn(Optional.of(17L));
        when(precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request))
                .thenReturn(result(input, request));

        assertThatThrownBy(() -> queryService.precheckLatest("005930", master(input),
                input.restrictionScreeningResult().marketWarningObservation(), request))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessage(mismatchMessage());
        var order = inOrder(store, precheckService);
        order.verify(store).findLatestObservationId("005930", request.evaluatedAt());
        order.verify(precheckService).precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request);
        verifyNoMoreInteractions(store, precheckService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"observationId", "evaluation", "masterAge", "basicInfoAge", "masterBatch", "marketWarningObservation"})
    void rejectsPrecheckForDifferentSelectedIdOrEvaluationInputs(String changed) {
        var input = input();
        var request = request();
        var otherRequest = new KisStockRestrictionFreshnessRequest(
                changed.equals("evaluation") ? request.evaluatedAt().plusNanos(1) : request.evaluatedAt(),
                changed.equals("masterAge") ? request.maxMasterAge().plusNanos(1) : request.maxMasterAge(),
                changed.equals("basicInfoAge") ? request.maxBasicInfoAge().plusNanos(1) : request.maxBasicInfoAge());
        var different = switch (changed) {
            case "observationId" -> new KisStockRestrictionAnalysisResult(new KisStockBasicInfoAnalysisResult(
                    18L, input.basicInfoAnalysis().response(), input.basicInfoAnalysis().screeningResult()), input.restrictionScreeningResult());
            case "masterBatch" -> combine(input.basicInfoAnalysis().response(), withMarketTimes(master(input), KOSDAQ,
                    request.evaluatedAt().minus(Duration.ofHours(3)), request.evaluatedAt().minus(Duration.ofHours(3)).plusSeconds(20)),
                    input.restrictionScreeningResult().marketWarningObservation());
            case "marketWarningObservation" -> combine(input.basicInfoAnalysis().response(), master(input), warnings(master(input), KOSPI));
            default -> input;
        };
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.of(17L));
        when(precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request))
                .thenReturn(result(different, otherRequest));

        assertThatThrownBy(() -> check(input, request)).isExactlyInstanceOf(IllegalStateException.class).hasMessage(mismatchMessage());
        assertSingleCall(input, request);
    }

    @Test
    void repeatedCallsUseFullSuppliedConditionsWithoutCachingOrMixingState() {
        var input = input();
        var request = request();
        var later = new KisStockRestrictionFreshnessRequest(request.evaluatedAt().plus(Duration.ofDays(2)),
                request.maxMasterAge(), request.maxBasicInfoAge());
        var firstResult = result(input, request);
        var laterResult = result(input, later);
        when(store.findLatestObservationId(symbol(input), request.evaluatedAt())).thenReturn(Optional.of(17L));
        when(store.findLatestObservationId(symbol(input), later.evaluatedAt())).thenReturn(Optional.of(17L));
        when(precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request)).thenReturn(firstResult);
        when(precheckService.precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), later)).thenReturn(laterResult);

        assertThat(check(input, request)).containsSame(firstResult);
        assertThat(check(input, later)).containsSame(laterResult);
        assertThat(check(input, request)).containsSame(firstResult);
        assertThat(laterResult).isNotEqualTo(firstResult);
        var order = inOrder(store, precheckService);
        for (var conditions : new KisStockRestrictionFreshnessRequest[]{request, later, request}) {
            order.verify(store).findLatestObservationId(symbol(input), conditions.evaluatedAt());
            order.verify(precheckService).precheck(eq(17L), same(master(input)),
                    same(input.restrictionScreeningResult().marketWarningObservation()), same(conditions));
        }
        verifyNoMoreInteractions(store, precheckService);
    }

    private Optional<KisStockRestrictionPrecheckResult> check(KisStockRestrictionAnalysisResult input, KisStockRestrictionFreshnessRequest request) {
        return queryService.precheckLatest(symbol(input), master(input), input.restrictionScreeningResult().marketWarningObservation(), request);
    }

    private void assertSingleCall(KisStockRestrictionAnalysisResult input, KisStockRestrictionFreshnessRequest request) {
        var order = inOrder(store, precheckService);
        order.verify(store).findLatestObservationId(symbol(input), request.evaluatedAt());
        order.verify(precheckService).precheck(17L, master(input), input.restrictionScreeningResult().marketWarningObservation(), request);
        verifyNoMoreInteractions(store, precheckService);
    }

    private static KisStockRestrictionAnalysisResult input() {
        return freshness(KOSDAQ, NO_EXCLUSION_SIGNAL_OBSERVED, FRESH).analysisResult();
    }

    private static String symbol(KisStockRestrictionAnalysisResult input) {
        return input.basicInfoAnalysis().response().requestedSymbol();
    }

    private static StockMasterBatchParseResult master(KisStockRestrictionAnalysisResult input) {
        return input.basicInfoAnalysis().screeningResult().observation().typeResolution().matchingResult().masterBatch();
    }

    private static KisStockRestrictionPrecheckResult result(KisStockRestrictionAnalysisResult input, KisStockRestrictionFreshnessRequest request) {
        return new KisStockRestrictionPrecheckPolicy().evaluate(new KisStockRestrictionFreshnessPolicy().evaluate(request, input));
    }

    private static String mismatchMessage() {
        return "Restriction precheck must preserve the selected observation ID, requested symbol, master batch, market warning observation and freshness request.";
    }

    private static Stream<Arguments> statusCombinations() {
        return Arrays.stream(KisStockMasterMarket.values()).flatMap(market -> Arrays.stream(KisStockRestrictionScreeningStatus.values())
                .flatMap(restriction -> Arrays.stream(KisStockRestrictionFreshnessStatus.values())
                        .map(status -> Arguments.of(market, restriction, status))));
    }

    private static Stream<RuntimeException> precheckFailures() {
        return Stream.of(new DataAccessResourceFailureException("Synthetic database failure."),
                new IllegalArgumentException("Synthetic parsing failure."), new IllegalStateException("Synthetic integrity failure."),
                new NoSuchElementException("Synthetic observation removed after selection."));
    }
}
