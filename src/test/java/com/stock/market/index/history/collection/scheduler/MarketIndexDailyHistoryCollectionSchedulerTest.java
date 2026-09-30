package com.stock.market.index.history.collection.scheduler;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionResult;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionService;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryCollectionProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import com.stock.market.index.history.collection.scheduler.config.MarketIndexDailyHistoryCollectionSchedulerProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketIndexDailyHistoryCollectionSchedulerTest {
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 23);
    private static final LocalDate FROM_DATE = LocalDate.of(2023, 9, 23);

    @Test
    void doesNotCollectWhenDisabled() {
        MarketIndexDailyHistoryCollectionService collectionService = mock(
                MarketIndexDailyHistoryCollectionService.class
        );
        MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy =
                mock(MarketIndexDailyHistoryCollectionDatePolicy.class);
        MarketIndexDailyHistoryCollectionScheduler scheduler = scheduler(
                collectionService,
                collectionDatePolicy,
                false,
                List.of("KOSPI")
        );

        scheduler.run();

        verifyNoInteractions(collectionService, collectionDatePolicy);
    }

    @Test
    void collectsConfiguredIndexesUsingLatestCompletedTradingDate() {
        MarketIndexDailyHistoryCollectionService collectionService = mock(
                MarketIndexDailyHistoryCollectionService.class
        );
        MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy =
                mock(MarketIndexDailyHistoryCollectionDatePolicy.class);
        when(collectionDatePolicy.getLatestCompletedTradingDate())
                .thenReturn(TO_DATE);
        when(collectionService.collect(any()))
                .thenAnswer(invocation -> {
                    MarketIndexDailyHistoryRequest request =
                            invocation.getArgument(0);
                    return MarketIndexDailyHistoryCollectionResult
                            .alreadyUpToDate(request);
                });
        MarketIndexDailyHistoryCollectionScheduler scheduler = scheduler(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("KOSPI", "KOSDAQ")
        );

        scheduler.run();

        InOrder inOrder = inOrder(collectionService);
        inOrder.verify(collectionService).collect(request("KOSPI"));
        inOrder.verify(collectionService).collect(request("KOSDAQ"));
        verify(collectionDatePolicy).getLatestCompletedTradingDate();
    }

    @Test
    void continuesWithNextIndexWhenCollectionFails() {
        MarketIndexDailyHistoryCollectionService collectionService = mock(
                MarketIndexDailyHistoryCollectionService.class
        );
        MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy =
                mock(MarketIndexDailyHistoryCollectionDatePolicy.class);
        MarketIndexDailyHistoryRequest firstRequest = request("KOSPI");
        MarketIndexDailyHistoryRequest secondRequest = request("KOSDAQ");
        when(collectionDatePolicy.getLatestCompletedTradingDate())
                .thenReturn(TO_DATE);
        when(collectionService.collect(firstRequest))
                .thenThrow(new IllegalStateException("KIS collection failed."));
        when(collectionService.collect(secondRequest))
                .thenReturn(MarketIndexDailyHistoryCollectionResult
                        .alreadyUpToDate(secondRequest));
        MarketIndexDailyHistoryCollectionScheduler scheduler = scheduler(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("KOSPI", "KOSDAQ")
        );

        assertThatNoException().isThrownBy(scheduler::run);

        verify(collectionService).collect(firstRequest);
        verify(collectionService).collect(secondRequest);
    }

    private MarketIndexDailyHistoryCollectionScheduler scheduler(
            MarketIndexDailyHistoryCollectionService collectionService,
            MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy,
            boolean enabled,
            List<String> benchmarkIds
    ) {
        return new MarketIndexDailyHistoryCollectionScheduler(
                collectionService,
                collectionDatePolicy,
                new MarketIndexDailyHistoryCollectionProperties(
                        LocalTime.of(20, 10),
                        benchmarkIds,
                        3
                ),
                new MarketIndexDailyHistoryCollectionSchedulerProperties(
                        enabled,
                        "0 25 20 * * MON-FRI",
                        "Asia/Seoul"
                )
        );
    }

    private MarketIndexDailyHistoryRequest request(String benchmarkId) {
        return new MarketIndexDailyHistoryRequest(
                benchmarkId,
                FROM_DATE,
                TO_DATE
        );
    }
}
