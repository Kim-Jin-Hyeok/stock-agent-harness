package com.stock.market.price.history.collection.scheduler;

import com.stock.market.price.history.DailyPriceHistoryRequest;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionResult;
import com.stock.market.price.history.collection.DailyPriceHistoryCollectionService;
import com.stock.market.price.history.collection.config.DailyPriceHistoryCollectionProperties;
import com.stock.market.price.history.collection.policy.DailyPriceCollectionDatePolicy;
import com.stock.market.price.history.collection.scheduler.config.DailyPriceHistoryCollectionSchedulerProperties;
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

class DailyPriceHistoryCollectionSchedulerTest {
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 23);
    private static final LocalDate FROM_DATE = LocalDate.of(2023, 9, 23);

    @Test
    void doesNotCollectWhenDisabled() {
        DailyPriceHistoryCollectionService collectionService = mock(
                DailyPriceHistoryCollectionService.class
        );
        DailyPriceCollectionDatePolicy collectionDatePolicy = mock(
                DailyPriceCollectionDatePolicy.class
        );
        DailyPriceHistoryCollectionScheduler scheduler = scheduler(
                collectionService,
                collectionDatePolicy,
                false,
                List.of("005930")
        );

        scheduler.run();

        verifyNoInteractions(collectionService, collectionDatePolicy);
    }

    @Test
    void collectsConfiguredSymbolsUsingLatestCompletedTradingDate() {
        DailyPriceHistoryCollectionService collectionService = mock(
                DailyPriceHistoryCollectionService.class
        );
        DailyPriceCollectionDatePolicy collectionDatePolicy = mock(
                DailyPriceCollectionDatePolicy.class
        );
        when(collectionDatePolicy.getLatestCompletedTradingDate())
                .thenReturn(TO_DATE);
        when(collectionService.collect(any()))
                .thenAnswer(invocation -> {
                    DailyPriceHistoryRequest request = invocation.getArgument(0);
                    return DailyPriceHistoryCollectionResult
                            .alreadyUpToDate(request);
                });
        DailyPriceHistoryCollectionScheduler scheduler = scheduler(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("005930", "000660")
        );
        DailyPriceHistoryRequest firstRequest = request("005930");
        DailyPriceHistoryRequest secondRequest = request("000660");

        scheduler.run();

        InOrder inOrder = inOrder(collectionService);
        inOrder.verify(collectionService).collect(firstRequest);
        inOrder.verify(collectionService).collect(secondRequest);
        verify(collectionDatePolicy).getLatestCompletedTradingDate();
    }

    @Test
    void continuesWithNextSymbolWhenCollectionFails() {
        DailyPriceHistoryCollectionService collectionService = mock(
                DailyPriceHistoryCollectionService.class
        );
        DailyPriceCollectionDatePolicy collectionDatePolicy = mock(
                DailyPriceCollectionDatePolicy.class
        );
        DailyPriceHistoryRequest firstRequest = request("005930");
        DailyPriceHistoryRequest secondRequest = request("000660");
        when(collectionDatePolicy.getLatestCompletedTradingDate())
                .thenReturn(TO_DATE);
        when(collectionService.collect(firstRequest))
                .thenThrow(new IllegalStateException("KIS collection failed."));
        when(collectionService.collect(secondRequest))
                .thenReturn(DailyPriceHistoryCollectionResult
                        .alreadyUpToDate(secondRequest));
        DailyPriceHistoryCollectionScheduler scheduler = scheduler(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("005930", "000660")
        );

        assertThatNoException().isThrownBy(scheduler::run);

        verify(collectionService).collect(firstRequest);
        verify(collectionService).collect(secondRequest);
    }

    private DailyPriceHistoryCollectionScheduler scheduler(
            DailyPriceHistoryCollectionService collectionService,
            DailyPriceCollectionDatePolicy collectionDatePolicy,
            boolean enabled,
            List<String> symbols
    ) {
        return new DailyPriceHistoryCollectionScheduler(
                collectionService,
                collectionDatePolicy,
                new DailyPriceHistoryCollectionProperties(
                        LocalTime.of(20, 10),
                        symbols,
                        3
                ),
                new DailyPriceHistoryCollectionSchedulerProperties(
                        enabled,
                        "0 15 20 * * MON-FRI",
                        "Asia/Seoul"
                )
        );
    }

    private DailyPriceHistoryRequest request(String symbol) {
        return new DailyPriceHistoryRequest(symbol, FROM_DATE, TO_DATE);
    }
}
