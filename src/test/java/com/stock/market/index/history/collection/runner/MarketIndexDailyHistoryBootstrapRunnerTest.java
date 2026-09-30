package com.stock.market.index.history.collection.runner;

import com.stock.market.index.history.MarketIndexDailyHistoryRequest;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionResult;
import com.stock.market.index.history.collection.MarketIndexDailyHistoryCollectionService;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryBootstrapProperties;
import com.stock.market.index.history.collection.config.MarketIndexDailyHistoryCollectionProperties;
import com.stock.market.index.history.collection.policy.MarketIndexDailyHistoryCollectionDatePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketIndexDailyHistoryBootstrapRunnerTest {
    private static final LocalDate TO_DATE = LocalDate.of(2026, 9, 23);
    private static final LocalDate FROM_DATE = LocalDate.of(2023, 9, 23);

    @Test
    void doesNotCollectWhenDisabled() {
        MarketIndexDailyHistoryCollectionService collectionService = mock(
                MarketIndexDailyHistoryCollectionService.class
        );
        MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy =
                mock(MarketIndexDailyHistoryCollectionDatePolicy.class);
        MarketIndexDailyHistoryBootstrapRunner runner = runner(
                collectionService,
                collectionDatePolicy,
                false,
                List.of("KOSPI")
        );

        runner.run(mock(ApplicationArguments.class));

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
        MarketIndexDailyHistoryBootstrapRunner runner = runner(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("KOSPI", "KOSDAQ")
        );
        MarketIndexDailyHistoryRequest firstRequest = request("KOSPI");
        MarketIndexDailyHistoryRequest secondRequest = request("KOSDAQ");

        runner.run(mock(ApplicationArguments.class));

        InOrder inOrder = inOrder(collectionService);
        inOrder.verify(collectionService).collect(firstRequest);
        inOrder.verify(collectionService).collect(secondRequest);
    }

    @Test
    void stopsWhenCollectionFails() {
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
        MarketIndexDailyHistoryBootstrapRunner runner = runner(
                collectionService,
                collectionDatePolicy,
                true,
                List.of("KOSPI", "KOSDAQ")
        );

        assertThatThrownBy(() ->
                runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("KIS collection failed.");

        verify(collectionService).collect(firstRequest);
        verify(collectionService, never()).collect(secondRequest);
    }

    private MarketIndexDailyHistoryBootstrapRunner runner(
            MarketIndexDailyHistoryCollectionService collectionService,
            MarketIndexDailyHistoryCollectionDatePolicy collectionDatePolicy,
            boolean enabled,
            List<String> benchmarkIds
    ) {
        return new MarketIndexDailyHistoryBootstrapRunner(
                collectionService,
                collectionDatePolicy,
                new MarketIndexDailyHistoryBootstrapProperties(enabled),
                new MarketIndexDailyHistoryCollectionProperties(
                        LocalTime.of(20, 10),
                        benchmarkIds,
                        3
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
