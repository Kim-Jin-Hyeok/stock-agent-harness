package com.stock.market.price.history.collection.backfill.runner;

import com.stock.broker.kis.config.KisProperties;
import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.TradingVenueScope;
import com.stock.market.price.history.collection.backfill.DailyPriceTradingValueBackfillService;
import com.stock.market.price.history.collection.backfill.result.DailyPriceTradingValueBackfillResult;
import com.stock.market.price.history.collection.backfill.result.DailyPriceTradingValueBackfillStatus;
import com.stock.market.price.history.collection.backfill.runner.config.DailyPriceTradingValueBackfillProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import com.stock.market.price.history.provider.kis.KisDailyPriceHistoryMarket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DailyPriceTradingValueBackfillRunnerTest {
    private static final LocalDate FROM = LocalDate.of(2026, 9, 21);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final DailyPriceHistoryRequest REQUEST = new DailyPriceHistoryRequest("005930", FROM, TO);
    private static final TradingVenueScope SCOPE = TradingVenueScope.INTEGRATED;
    private final DailyPriceTradingValueBackfillService service = mock(DailyPriceTradingValueBackfillService.class);
    private final DailyPriceCollectionDatePolicy datePolicy = mock(DailyPriceCollectionDatePolicy.class);
    private final KisProperties kisProperties = mock(KisProperties.class);

    @Test
    void doesNotCheckDependenciesOrCollectWhenDisabled() {
        runner(new DailyPriceTradingValueBackfillProperties(false, null, null, null, null, null))
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(service, datePolicy, kisProperties);
    }

    @ParameterizedTest
    @EnumSource(KisDailyPriceHistoryMarket.class)
    void callsExistingServiceExactlyOnceForMatchingConfiguredScope(KisDailyPriceHistoryMarket market) {
        TradingVenueScope scope = market.toTradingVenueScope();
        var properties = new DailyPriceTradingValueBackfillProperties(true, "005930", FROM, TO, scope, 10);
        when(kisProperties.enabled()).thenReturn(true);
        when(kisProperties.dailyPriceHistoryMarket()).thenReturn(market);
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO);
        when(service.backfill(REQUEST, scope)).thenReturn(new DailyPriceTradingValueBackfillResult(
                DailyPriceTradingValueBackfillStatus.NO_TARGETS, REQUEST, null, scope, 0, 0, 0));

        runner(properties).run(new DefaultApplicationArguments());

        verify(service).backfill(REQUEST, scope);
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsIncompleteEndDateWithoutCallingService(CapturedOutput output) {
        matchingKisProperties();
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO.minusDays(1));

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backfill toDate must not be after the latest completed trading date.");
        verifyNoInteractions(service);
        assertThat(output).doesNotContain("backfill started", "backfill finished");
    }

    @Test
    void acceptsEndDateEarlierThanLatestCompletedDate() {
        matchingKisProperties();
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO.plusDays(5));
        when(service.backfill(REQUEST, SCOPE)).thenReturn(noTargets());

        runner(enabledProperties()).run(new DefaultApplicationArguments());

        verify(service).backfill(REQUEST, SCOPE);
    }

    @ParameterizedTest
    @EnumSource(value = KisDailyPriceHistoryMarket.class, names = {"KRX", "NXT"})
    void rejectsScopeMismatchBeforeCallingServiceOrDatePolicy(KisDailyPriceHistoryMarket market) {
        when(kisProperties.enabled()).thenReturn(true);
        when(kisProperties.dailyPriceHistoryMarket()).thenReturn(market);

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backfill expectedVenueScope must match broker.kis.daily-price-history-market.");
        verifyNoInteractions(service, datePolicy);
    }

    @Test
    void rejectsDisabledKisInsteadOfReportingSuccess() {
        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Trading value backfill requires broker.kis.enabled=true.");
        verifyNoInteractions(service, datePolicy);
    }

    @Test
    void rejectsMissingCompletedDate() {
        matchingKisProperties();

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("latestCompletedTradingDate must not be null.");
        verifyNoInteractions(service);
    }

    @Test
    void propagatesDatePolicyFailureWithoutCallingService() {
        matchingKisProperties();
        var failure = new IllegalStateException("Calendar unavailable.");
        when(datePolicy.getLatestCompletedTradingDate()).thenThrow(failure);

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments())).isSameAs(failure);
        verifyNoInteractions(service);
    }

    @Test
    void propagatesProviderOrPersistenceFailureWithoutRetryOrCompletionLog(CapturedOutput output) {
        matchingKisProperties();
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO);
        var failure = new IllegalStateException("Backfill failed.");
        when(service.backfill(REQUEST, SCOPE)).thenThrow(failure);

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments())).isSameAs(failure);

        verify(service).backfill(REQUEST, SCOPE);
        verifyNoMoreInteractions(service);
        assertThat(output).contains("backfill started").doesNotContain("backfill finished");
    }

    @ParameterizedTest
    @EnumSource(DailyPriceTradingValueBackfillStatus.class)
    void logsActualStatusRangesAndRowCountsWithoutCredentials(
            DailyPriceTradingValueBackfillStatus status,
            CapturedOutput output
    ) {
        matchingKisProperties();
        when(kisProperties.dailyPriceHistoryMaxPages()).thenReturn(2);
        when(kisProperties.dailyPriceHistoryRequestDelay()).thenReturn(Duration.ofSeconds(1));
        when(kisProperties.appKey()).thenReturn("test-key-not-for-logs");
        when(kisProperties.appSecret()).thenReturn("test-secret-not-for-logs");
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO);
        var actualRange = new DailyPriceHistoryRequest("005930", FROM.plusDays(1), TO.minusDays(1));
        boolean noTargets = status == DailyPriceTradingValueBackfillStatus.NO_TARGETS;
        when(service.backfill(REQUEST, SCOPE)).thenReturn(new DailyPriceTradingValueBackfillResult(
                status, REQUEST, noTargets ? null : actualRange, SCOPE,
                noTargets ? 0 : 2, noTargets ? 0 : 4,
                status == DailyPriceTradingValueBackfillStatus.BACKFILLED ? 1 : 0));

        runner(enabledProperties()).run(new DefaultApplicationArguments());

        assertThat(output).contains("backfill started", "backfill finished", "status=" + status,
                "symbol=005930", "fromDate=2026-09-21", "toDate=2026-09-30", "expectedVenueScope=INTEGRATED",
                "maxRangeDays=10", "latestCompletedTradingDate=2026-09-30", "maxPages=2", "requestDelay=PT1S",
                "targetCount=" + (noTargets ? 0 : 2), "fetchedCount=" + (noTargets ? 0 : 4),
                "updatedCount=" + (status == DailyPriceTradingValueBackfillStatus.BACKFILLED ? 1 : 0));
        assertThat(output).doesNotContain("test-key-not-for-logs", "test-secret-not-for-logs", "KisProperties[");
        if (noTargets) {
            assertThat(output).contains("collectionRange=null");
        } else {
            assertThat(output).contains("collectionRange=" + actualRange);
        }
    }

    private void matchingKisProperties() {
        when(kisProperties.enabled()).thenReturn(true);
        when(kisProperties.dailyPriceHistoryMarket()).thenReturn(KisDailyPriceHistoryMarket.INTEGRATED);
    }

    private DailyPriceTradingValueBackfillResult noTargets() {
        return new DailyPriceTradingValueBackfillResult(
                DailyPriceTradingValueBackfillStatus.NO_TARGETS, REQUEST, null, SCOPE, 0, 0, 0);
    }

    private DailyPriceTradingValueBackfillRunner runner(DailyPriceTradingValueBackfillProperties properties) {
        return new DailyPriceTradingValueBackfillRunner(service, properties, datePolicy, kisProperties);
    }

    private DailyPriceTradingValueBackfillProperties enabledProperties() {
        return new DailyPriceTradingValueBackfillProperties(true, "005930", FROM, TO, SCOPE, 10);
    }
}
