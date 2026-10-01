package com.stock.market.index.history.collection.backfill.runner;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.collection.backfill.MarketIndexDailyHistoryBackfillService;
import com.stock.market.index.history.collection.backfill.result.MarketIndexDailyHistoryBackfillResult;
import com.stock.market.index.history.collection.backfill.result.MarketIndexDailyHistoryBackfillStatus;
import com.stock.market.index.history.collection.backfill.runner.config.MarketIndexDailyHistoryBackfillProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketIndexDailyHistoryBackfillRunnerTest {
    private static final LocalDate FROM = LocalDate.of(2023, 9, 25);
    private static final LocalDate TO = LocalDate.of(2025, 9, 29);
    private final MarketIndexDailyHistoryBackfillService service = mock(MarketIndexDailyHistoryBackfillService.class);
    private final MarketIndexDailyHistoryCollectionDatePolicy datePolicy =
            mock(MarketIndexDailyHistoryCollectionDatePolicy.class);

    @Test
    void doesNotCollectWhenDisabled() {
        runner(new MarketIndexDailyHistoryBackfillProperties(false, null, null, null))
                .run(new DefaultApplicationArguments());

        verifyNoInteractions(service, datePolicy);
    }

    @Test
    void callsBackfillOnceWithExplicitRange() {
        var request = new MarketIndexDailyHistoryRequest("KOSPI", FROM, TO);
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO);
        when(service.backfill(request)).thenReturn(new MarketIndexDailyHistoryBackfillResult(
                MarketIndexDailyHistoryBackfillStatus.NO_EARLIER_RANGE,
                request, null, 0, 0, null, null
        ));

        runner(enabledProperties()).run(new DefaultApplicationArguments());

        verify(service).backfill(request);
    }

    @Test
    void rejectsUnfinishedDateBeforeCallingService() {
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO.minusDays(1));

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Backfill toDate must not be after the latest completed trading date.");
        verifyNoInteractions(service);
    }

    @Test
    void propagatesFailureToApplicationStartup() {
        when(datePolicy.getLatestCompletedTradingDate()).thenReturn(TO);
        when(service.backfill(enabledProperties().toRequest()))
                .thenThrow(new IllegalStateException("Provider unavailable."));

        assertThatThrownBy(() -> runner(enabledProperties()).run(new DefaultApplicationArguments()))
                .hasMessage("Provider unavailable.");
    }

    private MarketIndexDailyHistoryBackfillRunner runner(MarketIndexDailyHistoryBackfillProperties properties) {
        return new MarketIndexDailyHistoryBackfillRunner(service, properties, datePolicy);
    }

    private MarketIndexDailyHistoryBackfillProperties enabledProperties() {
        return new MarketIndexDailyHistoryBackfillProperties(true, "KOSPI", FROM, TO);
    }
}
